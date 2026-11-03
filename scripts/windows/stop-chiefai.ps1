#requires -Version 7.4
param([Parameter(Mandatory)][string]$ConfigPath)
. "$PSScriptRoot/common.ps1"
try {
    $c = Get-ChiefConfig $ConfigPath
    $record = Read-ChiefProcessRecord $c
    if (-not $record) { Write-Output 'ChiefAI is not running.'; exit 0 }
    # The backend wrapper verifies its own child. No cross-account process privileges are needed here.
    Set-Content -LiteralPath (Join-Path $c.StateDir 'stop.request') -Value $record.Marker -Encoding ascii -NoNewline
    $deadline = [DateTime]::UtcNow.AddSeconds([int]$c.StopTimeoutSeconds)
    do {
        Start-Sleep -Seconds 1
        if (-not (Test-Path -LiteralPath (Join-Path $c.StateDir 'process.json'))) { Write-Output 'ChiefAI stopped.'; exit 0 }
    } while ([DateTime]::UtcNow -lt $deadline)
    throw 'The ChiefAI wrapper did not stop its identified process within the timeout.'
} catch { Write-Error "ChiefAI stop failed: $($_.Exception.Message)"; exit 1 }
