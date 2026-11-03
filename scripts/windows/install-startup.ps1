#requires -Version 7.4
param([Parameter(Mandatory)][string]$ConfigPath)
. "$PSScriptRoot/common.ps1"
try {
    $c = Get-ChiefConfig $ConfigPath
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    if (-not ([Security.Principal.WindowsPrincipal]$identity).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) { throw 'Run this installer as Administrator.' }
    $backend = Get-Credential -Message 'Conta dedicada para executar o backend ChiefAI'
    $supervisor = Get-Credential -Message 'Conta dedicada para executar o supervisor ChiefAI'
    if (-not $backend -or -not $supervisor) { throw 'Both task credentials are required.' }
    $pwsh = Join-Path $PSHOME 'pwsh.exe'
    $trigger = New-ScheduledTaskTrigger -AtStartup
    $settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -ExecutionTimeLimit ([TimeSpan]::Zero) -MultipleInstances IgnoreNew
    foreach ($item in @(@($c.BackendTask, 'start-chiefai.ps1', $backend), @($c.SupervisorTask, 'update-chiefai.ps1', $supervisor))) {
        $script = Join-Path $PSScriptRoot $item[1]
        $action = New-ScheduledTaskAction -Execute $pwsh -Argument ('-NoProfile -NonInteractive -File "{0}" -ConfigPath "{1}"' -f $script, $ConfigPath) -WorkingDirectory $c.AppHome
        $taskSettings = $settings
        if ($item[1] -eq 'update-chiefai.ps1') {
            $taskSettings = New-ScheduledTaskSettingsSet -StartWhenAvailable -ExecutionTimeLimit ([TimeSpan]::Zero) -MultipleInstances IgnoreNew -RestartCount 3 -RestartInterval ([TimeSpan]::FromMinutes(1))
        }
        $password = $item[2].GetNetworkCredential().Password
        try { Register-ScheduledTask -TaskName $item[0] -Action $action -Trigger $trigger -Settings $taskSettings -User $item[2].UserName -Password $password -RunLevel Limited -Force | Out-Null }
        finally { $password = $null }
    }
    # Delegate query/start on this one task, without making the supervisor a local administrator.
    $sid = ([Security.Principal.NTAccount]::new($supervisor.UserName)).Translate([Security.Principal.SecurityIdentifier]).Value
    $scheduler = New-Object -ComObject 'Schedule.Service'
    $scheduler.Connect()
    $task = $scheduler.GetFolder('\').GetTask($c.BackendTask)
    $task.SetSecurityDescriptor(($task.GetSecurityDescriptor(4) + "(A;;GRGX;;;$sid)"), 0)
    Write-Output 'Tasks installed. Start them manually once to verify configuration.'
} catch { Write-Error "Task installation failed: $($_.Exception.Message)"; exit 1 }
