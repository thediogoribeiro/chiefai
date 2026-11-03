#requires -Version 7.4
param([Parameter(Mandatory)][string]$ConfigPath)
. "$PSScriptRoot/common.ps1"
try {
    $c = Get-ChiefConfig $ConfigPath
    $p = Get-ChiefProcess $c
    $version = if (Test-Path $c.VersionFile) { (Get-Content $c.VersionFile -Raw).Trim() } else { 'unknown' }
    $health = 'unavailable'
    try { $health = (Invoke-RestMethod -Uri $c.HealthUrl -TimeoutSec 3 -MaximumRedirection 0).status } catch { }
    [pscustomobject]@{ Running = [bool]$p; ProcessId = if ($p) { $p.ProcessId } else { $null }; Commit = $version; Health = $health }
    if (-not $p) { exit 1 }
} catch { Write-Error "ChiefAI status failed: $($_.Exception.Message)"; exit 1 }
