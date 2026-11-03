#requires -Version 7.4
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-ChiefConfig([string] $ConfigPath) {
    if (-not [IO.Path]::IsPathFullyQualified($ConfigPath) -or -not (Test-Path -LiteralPath $ConfigPath -PathType Leaf)) {
        throw 'Configuration must be an existing absolute JSON file.'
    }
    $c = Get-Content -LiteralPath $ConfigPath -Raw | ConvertFrom-Json
    foreach ($key in @('RepoPath','AppHome','SpringConfigPath','JavaPath','GitPath','PsqlPath','PgIsReadyPath','JarPath','VersionFile','LogDir','StateDir','StagingDir')) {
        $value = [string]$c.$key
        if (-not $value -or -not [IO.Path]::IsPathFullyQualified($value)) { throw "Missing absolute configuration path: $key" }
    }
    if (-not $c.Remote -or $c.Remote -notmatch '^[A-Za-z0-9][A-Za-z0-9._-]*$') { throw 'Invalid remote name.' }
    if (-not $c.Branch -or $c.Branch -notmatch '^[A-Za-z0-9][A-Za-z0-9._/-]*$' -or $c.Branch.Contains('..')) { throw 'Invalid branch name.' }
    if (-not $c.RemoteUrl -or $c.RemoteUrl -match '://[^/]*@') { throw 'A fixed remote URL without embedded credentials is required.' }
    if ($c.BackendTask -notmatch '^[A-Za-z0-9._-]+$' -or $c.SupervisorTask -notmatch '^[A-Za-z0-9._-]+$' -or $c.BackendTask -eq $c.SupervisorTask -or -not $c.HealthUrl) { throw 'Invalid task or health configuration.' }
    if (-not $c.DatabaseHost -or [int]$c.DatabasePort -lt 1 -or [int]$c.DatabasePort -gt 65535 -or -not $c.DatabaseName -or -not $c.DatabaseUser) { throw 'Invalid PostgreSQL configuration.' }
    if (-not $c.CandidateJar -or [IO.Path]::IsPathFullyQualified([string]$c.CandidateJar) -or [string]$c.CandidateJar -match '(^|[\\/])\.\.([\\/]|$)') { throw 'CandidateJar must be a relative path within staging.' }
    $repoRoot = [IO.Path]::GetFullPath([string]$c.RepoPath).TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
    foreach ($outside in @($ConfigPath, $c.SpringConfigPath, $c.AppHome, $c.StagingDir, $c.LogDir, $c.StateDir, $c.JarPath, $c.VersionFile)) {
        $full = [IO.Path]::GetFullPath([string]$outside)
        if ($full.StartsWith($repoRoot, [StringComparison]::OrdinalIgnoreCase) -or $full.TrimEnd('\', '/') -eq $repoRoot.TrimEnd('\', '/')) {
            throw 'Configuration, staging, application, state and logs must be outside the Git checkout.'
        }
    }
    foreach ($key in @('FetchTimeoutSeconds','BuildTimeoutSeconds','StopTimeoutSeconds','StartTimeoutSeconds','DatabaseTimeoutSeconds')) {
        if ([int]$c.$key -lt 1 -or [int]$c.$key -gt 7200) { throw "Invalid timeout: $key" }
    }
    return $c
}
function Invoke-ChiefCommand([string] $Executable, [string[]] $Arguments, [string] $WorkingDirectory, [int] $TimeoutSeconds, [string] $Label, [switch] $IsolatedBuild) {
    $psi = [Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = $Executable; $psi.WorkingDirectory = $WorkingDirectory
    $psi.UseShellExecute = $false; $psi.CreateNoWindow = $true
    $psi.RedirectStandardOutput = $true; $psi.RedirectStandardError = $true
    foreach ($arg in $Arguments) { [void]$psi.ArgumentList.Add($arg) }
    if ($IsolatedBuild) {
        foreach ($key in @($psi.Environment.Keys)) {
            if ($key -match '^(OPENAI_|TELEGRAM_|GOOGLE_|GMAIL_|WHATSAPP_|CHIEFAI_|DATABASE_|POSTGRES_|PGPASSWORD|PGPASSFILE|SPRING_|INTERNAL_API_KEY)') {
                [void]$psi.Environment.Remove($key)
            }
        }
        $psi.Environment['OPENAI_ENABLED'] = 'false'
    }
    $p = [Diagnostics.Process]::new(); $p.StartInfo = $psi
    try {
        if (-not $p.Start()) { throw "$Label failed to start." }
        $stdout = $p.StandardOutput.ReadToEndAsync(); $stderr = $p.StandardError.ReadToEndAsync()
        if (-not $p.WaitForExit($TimeoutSeconds * 1000)) { $p.Kill($true); $p.WaitForExit(); throw "$Label timed out." }
        $output = $stdout.GetAwaiter().GetResult(); [void]$stderr.GetAwaiter().GetResult()
        if ($p.ExitCode -ne 0) { throw "$Label failed (exit code $($p.ExitCode))." }
        return $output.Trim()
    } finally { $p.Dispose() }
}
function Wait-ChiefDatabase($c) {
    $deadline = [DateTime]::UtcNow.AddSeconds([int]$c.DatabaseTimeoutSeconds)
    do {
        try {
            [void](Invoke-ChiefCommand $c.PgIsReadyPath @('-h', [string]$c.DatabaseHost, '-p', [string]$c.DatabasePort, '-d', [string]$c.DatabaseName, '-t', '2') $c.AppHome 4 'PostgreSQL readiness')
            return
        } catch { Start-Sleep -Seconds 2 }
    } while ([DateTime]::UtcNow -lt $deadline)
    throw 'PostgreSQL is unavailable within the configured timeout.'
}
function Test-ChiefProcessIdentity($c, $saved, $process) {
    if ($null -eq $process -or [string]$saved.Marker -notmatch '^[a-f0-9]{32}$') { return $false }
    $markerPattern = '(?:^|\s)-Dchiefai\.instance=' + [regex]::Escape([string]$saved.Marker) + '(?:\s|$)'
    $jarPattern = '(?:^|\s)-jar\s+(?:"' + [regex]::Escape([string]$c.JarPath) + '"|' + [regex]::Escape([string]$c.JarPath) + ')(?:\s|$)'
    return [string]::Equals([string]$process.ExecutablePath, [string]$c.JavaPath, [StringComparison]::OrdinalIgnoreCase) -and
        $process.CommandLine -match $markerPattern -and $process.CommandLine -match $jarPattern -and
        $process.CreationDate.ToUniversalTime().Ticks -eq [long]$saved.StartTicks
}
function Read-ChiefProcessRecord($c) {
    $path = Join-Path $c.StateDir 'process.json'
    if (-not (Test-Path -LiteralPath $path)) { return $null }
    try {
        $saved = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
        if ([string]$saved.Marker -notmatch '^[a-f0-9]{32}$' -or [int]$saved.ProcessId -le 0 -or [long]$saved.StartTicks -le 0) { throw 'Invalid record.' }
        return $saved
    } catch { throw 'ChiefAI process record is invalid; inspect process.json manually.' }
}
function Get-ChiefProcess($c) {
    $pidFile = Join-Path $c.StateDir 'process.json'
    if (-not (Test-Path -LiteralPath $pidFile)) { return $null }
    try {
        $saved = Read-ChiefProcessRecord $c
        $process = Get-CimInstance Win32_Process -Filter "ProcessId = $([int]$saved.ProcessId)" -ErrorAction Stop
        if ($null -eq $process) { return $null }
        if (-not (Test-ChiefProcessIdentity $c $saved $process)) { throw 'The recorded PID does not belong to ChiefAI.' }
        return $process
    } catch { throw 'ChiefAI process identity could not be verified. Inspect process.json manually; no process was stopped.' }
}
function Invoke-ChiefSql($c, [string]$Sql) {
    $env:PGHOST = [string]$c.DatabaseHost; $env:PGPORT = [string]$c.DatabasePort
    $env:PGDATABASE = [string]$c.DatabaseName; $env:PGUSER = [string]$c.DatabaseUser
    if ($env:POSTGRES_PASSWORD) { $env:PGPASSWORD = $env:POSTGRES_PASSWORD }
    return Invoke-ChiefCommand $c.PsqlPath @('-X', '-q', '-t', '-A', '-v', 'ON_ERROR_STOP=1', '-c', $Sql) $c.AppHome 15 'Database operation'
}
function Wait-ChiefHealth($c) {
    $deadline = [DateTime]::UtcNow.AddSeconds([int]$c.StartTimeoutSeconds)
    do {
        try {
            $health = Invoke-RestMethod -Uri $c.HealthUrl -TimeoutSec 3 -MaximumRedirection 0
            if ($health.status -eq 'UP') { return }
        } catch { }
        Start-Sleep -Seconds 2
    } while ([DateTime]::UtcNow -lt $deadline)
    throw 'ChiefAI health check did not become UP within the configured timeout.'
}
function Enter-ChiefLock([string]$Path) {
    try { return [IO.File]::Open($Path, 'OpenOrCreate', 'ReadWrite', 'None') }
    catch { throw 'Another ChiefAI operation already owns this lock.' }
}

function Assert-ChiefJar([string]$File) {
    if (-not (Test-Path -LiteralPath $File -PathType Leaf)) { throw 'ChiefAI JAR is missing.' }
    $zip = [IO.Compression.ZipFile]::OpenRead($File)
    try {
        if (-not $zip.GetEntry('META-INF/MANIFEST.MF') -or -not $zip.GetEntry('BOOT-INF/classes/com/chiefai/personalagent/PersonalAgentCoreApplication.class')) { throw 'Artefact is not an executable ChiefAI Spring Boot JAR.' }
        $reader = [IO.StreamReader]::new($zip.GetEntry('META-INF/MANIFEST.MF').Open())
        try { $manifest = $reader.ReadToEnd() } finally { $reader.Dispose() }
        if ($manifest -notmatch '(?m)^Main-Class: org\.springframework\.boot\.loader\.launch\.JarLauncher\s*$') { throw 'ChiefAI JAR manifest is invalid.' }
    } finally { $zip.Dispose() }
}
