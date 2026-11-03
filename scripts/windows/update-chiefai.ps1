#requires -Version 7.4
param([Parameter(Mandatory)][string]$ConfigPath, [switch]$TestLibraryOnly)
. "$PSScriptRoot/common.ps1"

function Write-ChiefLog($c, [string]$Message) {
    $file = Join-Path $c.LogDir 'supervisor.log'
    if ((Test-Path $file) -and (Get-Item $file).Length -gt 5MB) {
        for ($i = 3; $i -ge 1; $i--) {
            $source = "$file.$i"; $target = "$file.$($i + 1)"
            if (Test-Path $source) { Move-Item -LiteralPath $source -Destination $target -Force }
        }
        Move-Item -LiteralPath $file -Destination "$file.1" -Force
    }
    Add-Content -LiteralPath $file -Value ("{0:o} {1}" -f [DateTime]::UtcNow, $Message) -Encoding utf8
}
function Write-Version($c, [string]$Commit) {
    $tmp = "$($c.VersionFile).new"
    Set-Content -LiteralPath $tmp -Value $Commit -Encoding ascii -NoNewline
    Move-Item -LiteralPath $tmp -Destination $c.VersionFile -Force
}
function Wait-BackendTaskIdle($c) {
    $deadline = [DateTime]::UtcNow.AddSeconds([int]$c.StopTimeoutSeconds)
    while ((Get-ScheduledTask -TaskName $c.BackendTask).State -eq 'Running') {
        if ([DateTime]::UtcNow -ge $deadline) { throw 'Backend task did not become idle.' }
        Start-Sleep -Seconds 1
    }
}
function Assert-BackendTaskRunning($c) {
    if ((Get-ScheduledTask -TaskName $c.BackendTask).State -ne 'Running' -or -not (Read-ChiefProcessRecord $c)) {
        throw 'The ChiefAI backend task and process record must both be present.'
    }
}
function Start-BackendTask($c) {
    Wait-BackendTaskIdle $c
    Start-ScheduledTask -TaskName $c.BackendTask -ErrorAction Stop
    Wait-ChiefHealth $c
    Assert-BackendTaskRunning $c
}
function Get-ChiefSchemaFingerprint($c) {
    $fingerprint = Invoke-ChiefSql $c "SELECT md5(COALESCE(string_agg(installed_rank::text || ':' || COALESCE(version,'') || ':' || COALESCE(checksum::text,'') || ':' || success::text, ',' ORDER BY installed_rank), '')) FROM flyway_schema_history"
    if ($fingerprint -notmatch '^[a-f0-9]{32}$') { throw 'Cannot verify Flyway migration state.' }
    return $fingerprint
}
function Invoke-Update($c, [string]$Id) {
    if ($Id -notmatch '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$') { throw 'Invalid update id.' }
    $stage = Join-Path $c.StagingDir ("update-" + $Id)
    $stopped = $false; $swapped = $false; $createdStage = $false; $previous = ''; $candidate = ''; $schemaBefore = ''; $migrationChanges = ''
    $backup = "$($c.JarPath).previous"
    try {
        if (Test-Path -LiteralPath $stage) { throw 'Staging directory already exists.' }
        if (-not (Test-Path $c.JarPath -PathType Leaf) -or -not (Test-Path $c.VersionFile -PathType Leaf)) { throw 'Installed artefact or version is missing.' }
        $previous = (Get-Content -LiteralPath $c.VersionFile -Raw).Trim()
        if ($previous -notmatch '^[a-f0-9]{40,64}$') { throw 'Invalid installed commit.' }
        $git = [string]$c.GitPath; $repo = [string]$c.RepoPath
        $env:JAVA_HOME = Split-Path (Split-Path $c.JavaPath -Parent) -Parent
        [void](Invoke-ChiefCommand $git @('-C', $repo, 'check-ref-format', '--branch', $c.Branch) $c.AppHome 10 'Validate branch')
        $root = Invoke-ChiefCommand $git @('-C', $repo, 'rev-parse', '--show-toplevel') $c.AppHome 20 'Verify checkout root'
        if ([IO.Path]::GetFullPath($root).TrimEnd('\', '/') -ne [IO.Path]::GetFullPath($repo).TrimEnd('\', '/')) { throw 'RepoPath must identify the Git checkout root.' }
        $head = Invoke-ChiefCommand $git @('-C', $repo, 'rev-parse', 'HEAD') $c.AppHome 20 'Read checkout revision'
        $branch = Invoke-ChiefCommand $git @('-C', $repo, 'symbolic-ref', '--short', 'HEAD') $c.AppHome 20 'Read checkout branch'
        if ($branch -cne $c.Branch) { throw 'Checkout is not on the configured branch.' }
        $url = Invoke-ChiefCommand $git @('-C', $repo, 'remote', 'get-url', $c.Remote) $c.AppHome 20 'Read configured remote'
        if ($url -cne $c.RemoteUrl) { throw 'The Git remote differs from administrator configuration.' }
        $dirty = Invoke-ChiefCommand $git @('-C', $repo, 'status', '--porcelain', '--untracked-files=no') $c.AppHome 20 'Check checkout state'
        if ($dirty) { throw 'Tracked changes or conflicts exist in the checkout.' }
        $env:GIT_TERMINAL_PROMPT = '0'
        $ref = "refs/heads/$($c.Branch):refs/remotes/$($c.Remote)/$($c.Branch)"
        [void](Invoke-ChiefCommand $git @('-C', $repo, 'fetch', '--no-tags', '--', $c.Remote, $ref) $c.AppHome ([int]$c.FetchTimeoutSeconds) 'Git fetch')
        $candidate = Invoke-ChiefCommand $git @('-C', $repo, 'rev-parse', "refs/remotes/$($c.Remote)/$($c.Branch)^{commit}") $c.AppHome 20 'Read remote revision'
        if ($head -notmatch '^[a-f0-9]{40,64}$' -or $candidate -notmatch '^[a-f0-9]{40,64}$') { throw 'Invalid revision.' }
        [void](Invoke-ChiefCommand $git @('-C', $repo, 'merge-base', '--is-ancestor', $head, $candidate) $c.AppHome 20 'Check fast-forward')
        [void](Invoke-ChiefCommand $git @('-C', $repo, 'merge-base', '--is-ancestor', $previous, $candidate) $c.AppHome 20 'Check installed revision')
        if ($candidate -eq $previous) {
            if ($head -ne $candidate) { throw 'Checkout revision does not match the installed version.' }
            [void](Invoke-ChiefSql $c "UPDATE system_update SET status='NO_CHANGES', completed_at=now(), previous_commit='$previous', new_commit='$candidate' WHERE id='$Id' AND status='RUNNING'")
            Write-ChiefLog $c "Update ${Id}: no changes."
            return
        }
        $migrationChanges = Invoke-ChiefCommand $git @('-C', $repo, 'diff', '--name-only', $previous, $candidate, '--', 'src/main/resources/db/migration') $c.AppHome 20 'Inspect versioned migration changes'
        [void](New-Item -ItemType Directory -Path $c.StagingDir -Force)
        [void](New-Item -ItemType Directory -Path $stage)
        $createdStage = $true
        [void](Invoke-ChiefCommand $git @('clone', '--no-hardlinks', '--no-checkout', '--', $repo, $stage) $c.AppHome ([int]$c.FetchTimeoutSeconds) 'Create staging checkout')
        [void](Invoke-ChiefCommand $git @('-C', $stage, 'fetch', '--no-tags', '--', $repo, $candidate) $c.AppHome ([int]$c.FetchTimeoutSeconds) 'Transfer candidate to staging')
        [void](Invoke-ChiefCommand $git @('-C', $stage, 'checkout', '--detach', $candidate) $c.AppHome 60 'Checkout candidate')
        $maven = Join-Path $stage 'mvnw.cmd'
        if (-not (Test-Path $maven -PathType Leaf)) { throw 'Candidate Maven wrapper is missing.' }
        [void](Invoke-ChiefCommand $env:ComSpec @('/d', '/s', '/c', 'mvnw.cmd --batch-mode verify') $stage ([int]$c.BuildTimeoutSeconds) 'Candidate build and tests' -IsolatedBuild)
        $candidateJar = Join-Path $stage $c.CandidateJar
        Assert-ChiefJar $candidateJar
        # Recheck the checkout immediately before touching the running instance.
        $dirty = Invoke-ChiefCommand $git @('-C', $repo, 'status', '--porcelain', '--untracked-files=no') $c.AppHome 20 'Recheck checkout state'
        $currentHead = Invoke-ChiefCommand $git @('-C', $repo, 'rev-parse', 'HEAD') $c.AppHome 20 'Recheck checkout revision'
        $currentBranch = Invoke-ChiefCommand $git @('-C', $repo, 'symbolic-ref', '--short', 'HEAD') $c.AppHome 20 'Recheck checkout branch'
        if ($dirty -or $currentHead -ne $head -or $currentBranch -cne $c.Branch) { throw 'Checkout changed during candidate validation.' }
        [void](Invoke-ChiefSql $c "UPDATE system_update SET previous_commit='$previous', new_commit='$candidate' WHERE id='$Id' AND status='RUNNING'")
        $schemaBefore = Get-ChiefSchemaFingerprint $c
        Assert-BackendTaskRunning $c
        [void](Invoke-ChiefCommand (Join-Path $PSHOME 'pwsh.exe') @('-NoProfile', '-NonInteractive', '-File', (Join-Path $PSScriptRoot 'stop-chiefai.ps1'), '-ConfigPath', $ConfigPath) $c.AppHome ([int]$c.StopTimeoutSeconds + 10) 'Stop ChiefAI')
        $stopped = $true
        Wait-BackendTaskIdle $c
        $hooks = Join-Path $stage 'disabled-hooks'; [void](New-Item -ItemType Directory -Path $hooks)
        [void](Invoke-ChiefCommand $git @('-c', "core.hooksPath=$hooks", '-c', 'merge.autoStash=false', '-C', $repo, 'merge', '--ff-only', '--no-overwrite-ignore', '--no-edit', $candidate) $c.AppHome 60 'Fast-forward checkout')
        $newJar = "$($c.JarPath).candidate"
        Copy-Item -LiteralPath $candidateJar -Destination $newJar -Force
        [IO.File]::Replace($newJar, $c.JarPath, $backup, $true)
        $swapped = $true
        Write-Version $c $candidate
        Start-BackendTask $c
        [void](Invoke-ChiefSql $c "UPDATE system_update SET status='SUCCEEDED', completed_at=now() WHERE id='$Id' AND status='RUNNING'")
        Write-ChiefLog $c "Update ${Id}: succeeded."
    } catch {
        $status = if ($swapped) { 'FAILED_START' } else { 'FAILED_PRE_SWITCH' }
        $summary = if ($swapped) { 'Falha no arranque. Rollback do JAR tentado; migrations podem exigir intervencao manual.' } else { 'Falha antes da ativacao. Consulta o log operacional do supervisor.' }
        Write-ChiefLog $c "Update ${Id}: $status. $($_.Exception.Message)"
        if ($stopped) {
            try {
                if ($swapped) {
                    # The wrapper owns the process and only it may terminate the identified backend.
                    [void](Invoke-ChiefCommand (Join-Path $PSHOME 'pwsh.exe') @('-NoProfile', '-NonInteractive', '-File', (Join-Path $PSScriptRoot 'stop-chiefai.ps1'), '-ConfigPath', $ConfigPath) $c.AppHome ([int]$c.StopTimeoutSeconds + 10) 'Stop candidate')
                    if ($migrationChanges -or -not $schemaBefore -or (Get-ChiefSchemaFingerprint $c) -ne $schemaBefore) {
                        throw 'Versioned migrations or Flyway state changed, or cannot be verified. Automatic artefact rollback is unsafe; manual intervention is required.'
                    }
                    if (-not (Test-Path -LiteralPath $backup)) { throw 'Previous JAR is missing.' }
                    [IO.File]::Replace($backup, $c.JarPath, "$($c.JarPath).failed", $true)
                    Write-Version $c $previous
                }
                Start-BackendTask $c
            } catch {
                $status = 'ROLLBACK_FAILED'
                $summary = 'Recuperacao suspensa ou falhada. Verificar Flyway e JAR anterior; intervencao manual necessaria. Migrations nao sao revertidas.'
                Write-ChiefLog $c "Update ${Id}: recovery failed."
            }
        }
        [void](Invoke-ChiefSql $c "UPDATE system_update SET status='$status', completed_at=now(), error_summary='$summary' WHERE id='$Id' AND status='RUNNING'")
    } finally {
        # This directory is created solely for this request, outside the checkout and data directories.
        if ($createdStage -and (Test-Path -LiteralPath $stage)) { Remove-Item -LiteralPath $stage -Recurse -Force }
    }
}
if ($TestLibraryOnly) { return }

try {
    $c = Get-ChiefConfig $ConfigPath
    foreach ($dir in @($c.LogDir, $c.StateDir, $c.StagingDir)) { [void](New-Item -ItemType Directory -Path $dir -Force) }
    $lock = Enter-ChiefLock (Join-Path $c.StateDir 'supervisor.lock')
    try {
        Wait-ChiefDatabase $c
        $schemaDeadline = [DateTime]::UtcNow.AddSeconds([int]$c.StartTimeoutSeconds)
        while ($true) {
            try {
                [void](Invoke-ChiefSql $c "UPDATE system_update SET status='ROLLBACK_FAILED', completed_at=now(), error_summary='Supervisor interrompido durante atualizacao; verificar JAR, versao e migrations manualmente.' WHERE status='RUNNING'")
                break
            } catch {
                if ([DateTime]::UtcNow -ge $schemaDeadline) { throw 'Update table is unavailable; start the backend and check Flyway.' }
                Start-Sleep -Seconds 3
            }
        }
        Write-ChiefLog $c 'Supervisor started.'
        while ($true) {
            try {
                $id = Invoke-ChiefSql $c "UPDATE system_update SET status='RUNNING', started_at=now() WHERE id=(SELECT id FROM system_update WHERE status='REQUESTED' ORDER BY requested_at FOR UPDATE SKIP LOCKED LIMIT 1) RETURNING id"
                if ($id) { Invoke-Update $c $id }
            } catch { Write-ChiefLog $c 'Supervisor could not process the queue; will retry.' }
            Start-Sleep -Seconds 5
        }
    } finally { if ($lock) { $lock.Dispose() } }
} catch { Write-Error "ChiefAI supervisor failed: $($_.Exception.Message)"; exit 1 }
