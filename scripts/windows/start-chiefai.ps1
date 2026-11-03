#requires -Version 7.4
param([Parameter(Mandatory)][string]$ConfigPath)
. "$PSScriptRoot/common.ps1"
try {
    $c = Get-ChiefConfig $ConfigPath
    Set-Location -LiteralPath $c.AppHome
    foreach ($p in @($c.JavaPath, $c.GitPath, $c.PsqlPath, $c.PgIsReadyPath, $c.JarPath, $c.VersionFile, $c.SpringConfigPath)) {
        if (-not (Test-Path -LiteralPath $p -PathType Leaf)) { throw 'A required executable, JAR or version file is missing.' }
    }
    foreach ($p in @($c.LogDir, $c.StateDir)) { [void](New-Item -ItemType Directory -Path $p -Force) }
    $lockPath = Join-Path $c.StateDir 'backend.lock'
    $guard = Enter-ChiefLock $lockPath
    $java = $null
    try {
        if (Get-ChiefProcess $c) { throw 'The identified ChiefAI process is already running.' }
        $version = (Get-Content -LiteralPath $c.VersionFile -Raw).Trim()
        if ($version -notmatch '^[a-f0-9]{40,64}$') { throw 'Invalid installed commit file.' }
        [void](Invoke-ChiefCommand $c.JavaPath @('-version') $c.AppHome 10 'Java check')
        # Java writes its version to stderr; use release metadata from the Java executable's JDK directory.
        $release = Join-Path (Split-Path (Split-Path $c.JavaPath -Parent) -Parent) 'release'
        if (-not (Test-Path $release) -or -not ((Get-Content $release -Raw) -match '(?m)^JAVA_VERSION="21([.+-][^\"]*)?"')) { throw 'Java 21 is required.' }
        [void](Invoke-ChiefCommand $c.GitPath @('--version') $c.AppHome 10 'Git check')
        Assert-ChiefJar $c.JarPath
        Wait-ChiefDatabase $c
        [void](Invoke-ChiefSql $c 'SELECT 1')
        $errorLog = Join-Path $c.LogDir 'launcher-error.log'
        if (Test-Path $errorLog) {
            $old = Join-Path $c.LogDir 'launcher-error.previous.log'
            Move-Item -LiteralPath $errorLog -Destination $old -Force
        }
        $stopFile = Join-Path $c.StateDir 'stop.request'
        if (Test-Path $stopFile) { Remove-Item -LiteralPath $stopFile -Force }
        $marker = [guid]::NewGuid().ToString('N')
        $env:CHIEFAI_LOG_DIR = [string]$c.LogDir
        $env:CHIEFAI_SYSTEM_UPDATES_ENABLED = 'true'
        $env:CHIEFAI_SYSTEM_VERSION_FILE = [string]$c.VersionFile
        $env:DATABASE_URL = "jdbc:postgresql://$($c.DatabaseHost):$($c.DatabasePort)/$($c.DatabaseName)"
        $env:POSTGRES_USER = [string]$c.DatabaseUser
        $javaArgs = @("-Dchiefai.instance=$marker", '-Dlogging.config=classpath:logback-windows.xml', ('"-Dspring.config.additional-location=file:' + $c.SpringConfigPath.Replace('\', '/') + '"'), '-jar', ('"' + $c.JarPath + '"'))
        $java = Start-Process -FilePath $c.JavaPath -ArgumentList $javaArgs -WorkingDirectory $c.AppHome -WindowStyle Hidden -RedirectStandardOutput 'NUL' -RedirectStandardError $errorLog -PassThru
        $proc = Get-CimInstance Win32_Process -Filter "ProcessId = $($java.Id)"
        if ($null -eq $proc) { throw 'ChiefAI process exited during startup.' }
        $state = @{ ProcessId = $java.Id; Marker = $marker; StartTicks = $proc.CreationDate.ToUniversalTime().Ticks }
        $temp = Join-Path $c.StateDir 'process.json.tmp'
        $state | ConvertTo-Json -Compress | Set-Content -LiteralPath $temp -Encoding utf8
        Move-Item -LiteralPath $temp -Destination (Join-Path $c.StateDir 'process.json') -Force
        Wait-ChiefHealth $c
        $requestedStop = $false
        while (-not $java.HasExited) {
            if ((Test-Path -LiteralPath $stopFile) -and (Get-Content -LiteralPath $stopFile -Raw).Trim() -eq $marker) {
                if (-not (Get-ChiefProcess $c)) { throw 'ChiefAI process identity could not be verified.' }
                $requestedStop = $true
                Stop-Process -Id $java.Id -ErrorAction Stop
                if (-not $java.WaitForExit([int]$c.StopTimeoutSeconds * 1000)) { throw 'ChiefAI process did not stop.' }
                Remove-Item -LiteralPath $stopFile -Force -ErrorAction SilentlyContinue
                break
            }
            if ((Test-Path $errorLog) -and (Get-Item $errorLog).Length -gt 10MB) {
                if (Get-ChiefProcess $c) { Stop-Process -Id $java.Id -ErrorAction Stop; [void]$java.WaitForExit([int]$c.StopTimeoutSeconds * 1000) }
                throw 'Launcher error log exceeded 10 MB; backend stopped. Inspect configuration.'
            }
            Start-Sleep -Seconds 1
            $java.Refresh()
        }
        Remove-Item -LiteralPath (Join-Path $c.StateDir 'process.json') -Force -ErrorAction SilentlyContinue
        if (-not $requestedStop) { throw 'ChiefAI backend exited unexpectedly.' }
    } finally {
        # The original child handle is retained: cleanup never searches for other Java processes.
        if ($null -ne $java) {
            try { if (-not $java.HasExited) { $java.Kill(); [void]$java.WaitForExit([int]$c.StopTimeoutSeconds * 1000) } } catch { }
            $java.Dispose()
        }
        if ($guard) { $guard.Dispose() }
    }
} catch { Write-Error "ChiefAI start failed: $($_.Exception.Message)"; exit 1 }
