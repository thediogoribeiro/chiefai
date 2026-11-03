#requires -Version 7.4
# Pester 5.7+. All service/process/database operations are mocked; Git only uses TestDrive.
BeforeAll {
    . "$PSScriptRoot/../update-chiefai.ps1" -ConfigPath 'unused-in-library-mode' -TestLibraryOnly
    function Test-Git([string[]]$GitArgs) {
        Invoke-ChiefCommand (Get-Command git).Source $GitArgs $TestDrive 20 'Fixture Git'
    }
    function Add-RemoteCommit {
        Add-Content -LiteralPath (Join-Path $script:remote 'README.md') -Value 'next revision'
        [void](Test-Git @('-C', $script:remote, 'add', 'README.md'))
        [void](Test-Git @('-C', $script:remote, 'commit', '-m', 'candidate'))
        $script:newCommit = Test-Git @('-C', $script:remote, 'rev-parse', 'HEAD')
    }
    function New-TestJar([string]$File) {
        [void](New-Item -ItemType Directory -Path (Split-Path $File -Parent) -Force)
        $zip = [IO.Compression.ZipFile]::Open($File, 'Create')
        try {
            foreach ($name in @('META-INF/MANIFEST.MF', 'BOOT-INF/classes/com/chiefai/personalagent/PersonalAgentCoreApplication.class')) {
                $entry = $zip.CreateEntry($name)
                $writer = [IO.StreamWriter]::new($entry.Open())
                try { $writer.Write("Main-Class: org.springframework.boot.loader.launch.JarLauncher`n") } finally { $writer.Dispose() }
            }
        } finally { $zip.Dispose() }
    }
}
Describe 'Controlled update with a local Git repository' {
    BeforeEach {
        $script:root = Join-Path $TestDrive ([guid]::NewGuid().ToString('N'))
        [void](New-Item -ItemType Directory -Path $script:root)
        $script:remote = Join-Path $script:root 'remote'
        $repo = Join-Path $script:root 'checkout'
        [void](Test-Git @('init', '-b', 'main', $script:remote))
        [void](Test-Git @('-C', $script:remote, 'config', 'user.name', 'ChiefAI Test'))
        [void](Test-Git @('-C', $script:remote, 'config', 'user.email', 'test@example.invalid'))
        Set-Content -LiteralPath (Join-Path $script:remote 'README.md') -Value 'original'
        Set-Content -LiteralPath (Join-Path $script:remote 'mvnw.cmd') -Value '@exit /b 0'
        [void](Test-Git @('-C', $script:remote, 'add', '.'))
        [void](Test-Git @('-C', $script:remote, 'commit', '-m', 'initial'))
        $script:oldCommit = Test-Git @('-C', $script:remote, 'rev-parse', 'HEAD')
        [void](Test-Git @('clone', '--', $script:remote, $repo))
        $script:c = [pscustomobject]@{
            RepoPath = $repo; AppHome = $script:root; GitPath = (Get-Command git).Source
            JavaPath = (Join-Path $script:root 'jdk/bin/java.exe')
            Remote = 'origin'; RemoteUrl = $script:remote; Branch = 'main'; BackendTask = 'ChiefAI-Test-NeverInstalled'
            JarPath = (Join-Path $script:root 'chiefai.jar'); VersionFile = (Join-Path $script:root 'commit.txt')
            CandidateJar = 'target/candidate.jar'; StagingDir = (Join-Path $script:root 'staging')
            StateDir = $script:root; LogDir = $script:root
            FetchTimeoutSeconds = 20; BuildTimeoutSeconds = 20; StopTimeoutSeconds = 2
        }
        Set-Content -LiteralPath $script:c.JarPath -Value 'previous-artefact' -NoNewline
        Set-Content -LiteralPath $script:c.VersionFile -Value $script:oldCommit -NoNewline
        $script:statements = [Collections.Generic.List[string]]::new()
        $script:starts = 0
        Mock Invoke-ChiefSql { param($c, $Sql) $script:statements.Add($Sql); return '' }
        Mock Write-ChiefLog { }
        Mock Assert-BackendTaskRunning { }
        Mock Wait-BackendTaskIdle { }
        Mock Get-ChiefSchemaFingerprint { return ('b' * 32) }
        Mock Start-BackendTask { $script:starts++ }
        Mock Invoke-ChiefCommand -ParameterFilter { $Label -in @('Stop ChiefAI', 'Stop candidate') } { return '' }
        Mock Invoke-ChiefCommand -ParameterFilter { $Label -eq 'Candidate build and tests' } {
            param($Executable, $Arguments, $WorkingDirectory)
            New-TestJar (Join-Path $WorkingDirectory $script:c.CandidateJar)
            return ''
        }
        $script:id = [guid]::NewGuid().ToString()
    }
    It 'does not build or restart when no new commit exists' {
        Invoke-Update $script:c $script:id
        ($script:statements -join "`n") | Should -Match "status='NO_CHANGES'"
        Should -Invoke Invoke-ChiefCommand -Times 0 -ParameterFilter { $Label -in @('Stop ChiefAI', 'Candidate build and tests') }
        $script:starts | Should -Be 0
    }
    It 'preserves tracked local modifications' {
        $file = Join-Path $script:c.RepoPath 'README.md'
        Set-Content -LiteralPath $file -Value 'local work'
        Invoke-Update $script:c $script:id
        Get-Content -LiteralPath $file -Raw | Should -Match 'local work'
        ($script:statements -join "`n") | Should -Match "status='FAILED_PRE_SWITCH'"
        $script:starts | Should -Be 0
    }
    It 'preserves staged local modifications' {
        Set-Content -LiteralPath (Join-Path $script:c.RepoPath 'README.md') -Value 'staged work'
        [void](Test-Git @('-C', $script:c.RepoPath, 'add', 'README.md'))
        Invoke-Update $script:c $script:id
        Test-Git @('-C', $script:c.RepoPath, 'diff', '--cached') | Should -Match 'staged work'
        ($script:statements -join "`n") | Should -Match "status='FAILED_PRE_SWITCH'"
    }
    It 'rejects an administrator remote mismatch before fetch' {
        $script:c.RemoteUrl = 'different-local-remote'
        Invoke-Update $script:c $script:id
        Should -Invoke Invoke-ChiefCommand -Times 0 -ParameterFilter { $Label -eq 'Git fetch' }
        ($script:statements -join "`n") | Should -Match "status='FAILED_PRE_SWITCH'"
    }
    It 'does not stop or activate after a failed build' {
        Add-RemoteCommit
        Mock Invoke-ChiefCommand -ParameterFilter { $Label -eq 'Candidate build and tests' } { throw 'substitute build failed' }
        Invoke-Update $script:c $script:id
        Get-Content -LiteralPath $script:c.JarPath -Raw | Should -Be 'previous-artefact'
        Should -Invoke Invoke-ChiefCommand -Times 0 -ParameterFilter { $Label -eq 'Stop ChiefAI' }
        ($script:statements -join "`n") | Should -Match "status='FAILED_PRE_SWITCH'"
    }
    It 'rejects a malformed candidate JAR before stopping' {
        Add-RemoteCommit
        Mock Invoke-ChiefCommand -ParameterFilter { $Label -eq 'Candidate build and tests' } {
            param($Executable, $Arguments, $WorkingDirectory)
            $path = Join-Path $WorkingDirectory $script:c.CandidateJar
            [void](New-Item -ItemType Directory -Path (Split-Path $path -Parent) -Force)
            Set-Content -LiteralPath $path -Value 'not a JAR'
        }
        Invoke-Update $script:c $script:id
        Should -Invoke Invoke-ChiefCommand -Times 0 -ParameterFilter { $Label -eq 'Stop ChiefAI' }
        Get-Content -LiteralPath $script:c.JarPath -Raw | Should -Be 'previous-artefact'
    }
    It 'fast-forwards and activates a validated candidate' {
        Add-RemoteCommit
        Invoke-Update $script:c $script:id
        Test-Git @('-C', $script:c.RepoPath, 'rev-parse', 'HEAD') | Should -Be $script:newCommit
        Get-Content -LiteralPath $script:c.VersionFile -Raw | Should -Be $script:newCommit
        Get-Content -LiteralPath "$($script:c.JarPath).previous" -Raw | Should -Be 'previous-artefact'
        ($script:statements -join "`n") | Should -Match "status='SUCCEEDED'"
        $script:starts | Should -Be 1
    }
    It 'attempts artefact rollback after startup failure when Flyway is unchanged' {
        Add-RemoteCommit
        Mock Start-BackendTask { $script:starts++; if ($script:starts -eq 1) { throw 'substitute health failure' } }
        Invoke-Update $script:c $script:id
        Get-Content -LiteralPath $script:c.JarPath -Raw | Should -Be 'previous-artefact'
        Get-Content -LiteralPath $script:c.VersionFile -Raw | Should -Be $script:oldCommit
        ($script:statements -join "`n") | Should -Match "status='FAILED_START'"
        $script:starts | Should -Be 2
    }
    It 'requires manual intervention after a Flyway change' {
        Add-RemoteCommit
        $script:fingerprints = 0
        Mock Get-ChiefSchemaFingerprint { $script:fingerprints++; if ($script:fingerprints -eq 1) { return ('b' * 32) }; return ('c' * 32) }
        Mock Start-BackendTask { $script:starts++; throw 'substitute health failure' }
        Invoke-Update $script:c $script:id
        ($script:statements -join "`n") | Should -Match "status='ROLLBACK_FAILED'"
        Test-Path -LiteralPath "$($script:c.JarPath).previous" | Should -BeTrue
        $script:starts | Should -Be 1
    }
    It 'does not assume rollback is safe when candidate migration files changed' {
        $migrationDir = Join-Path $script:remote 'src/main/resources/db/migration'
        [void](New-Item -ItemType Directory -Path $migrationDir -Force)
        Set-Content -LiteralPath (Join-Path $migrationDir 'V99__test.sql') -Value 'SELECT 1;'
        [void](Test-Git @('-C', $script:remote, 'add', '.'))
        [void](Test-Git @('-C', $script:remote, 'commit', '-m', 'candidate migration'))
        Mock Start-BackendTask { $script:starts++; throw 'substitute health failure' }
        Invoke-Update $script:c $script:id
        ($script:statements -join "`n") | Should -Match "status='ROLLBACK_FAILED'"
        $script:starts | Should -Be 1
    }
    It 'preserves ignored local configuration when the candidate starts tracking that path' {
        $file = Join-Path $script:c.RepoPath 'settings.local'
        Set-Content -LiteralPath (Join-Path $script:c.RepoPath '.git/info/exclude') -Value 'settings.local'
        Set-Content -LiteralPath $file -Value 'private local config'
        Set-Content -LiteralPath (Join-Path $script:remote 'settings.local') -Value 'upstream file'
        [void](Test-Git @('-C', $script:remote, 'add', 'settings.local'))
        [void](Test-Git @('-C', $script:remote, 'commit', '-m', 'collision'))
        Invoke-Update $script:c $script:id
        Get-Content -LiteralPath $file -Raw | Should -Match 'private local config'
        Get-Content -LiteralPath $script:c.JarPath -Raw | Should -Be 'previous-artefact'
        ($script:statements -join "`n") | Should -Match "status='FAILED_PRE_SWITCH'"
    }
    It 'does not delete a pre-existing staging directory' {
        $preexisting = Join-Path $script:c.StagingDir "update-$($script:id)"
        [void](New-Item -ItemType Directory -Path $preexisting -Force)
        Set-Content -LiteralPath (Join-Path $preexisting 'keep.txt') -Value 'keep'
        Invoke-Update $script:c $script:id
        Test-Path -LiteralPath (Join-Path $preexisting 'keep.txt') | Should -BeTrue
    }
}
Describe 'Locks and process identity' {
    It 'prevents concurrent supervisors' {
        $path = Join-Path $TestDrive 'exclusive.lock'
        $first = Enter-ChiefLock $path
        try { { Enter-ChiefLock $path } | Should -Throw '*lock*' } finally { $first.Dispose() }
        $next = Enter-ChiefLock $path
        $next.Dispose()
    }
    It 'matches only the exact Java executable, marker, JAR and creation time' {
        $created = [datetime]::UtcNow
        $cfg = [pscustomobject]@{ JavaPath = 'C:\Java\bin\java.exe'; JarPath = 'C:\ChiefAI\chiefai.jar' }
        $saved = [pscustomobject]@{ Marker = ('a' * 32); StartTicks = $created.Ticks }
        $p = [pscustomobject]@{ ExecutablePath = $cfg.JavaPath; CommandLine = 'java.exe -Dchiefai.instance=' + $saved.Marker + ' -jar "' + $cfg.JarPath + '"'; CreationDate = $created }
        Test-ChiefProcessIdentity $cfg $saved $p | Should -BeTrue
        $p.CommandLine = 'java.exe -jar "C:\Other\application.jar"'
        Test-ChiefProcessIdentity $cfg $saved $p | Should -BeFalse
        $p.CommandLine = 'java.exe -Dchiefai.instance=' + $saved.Marker + 'suffix -jar "' + $cfg.JarPath + '"'
        Test-ChiefProcessIdentity $cfg $saved $p | Should -BeFalse
    }
}
