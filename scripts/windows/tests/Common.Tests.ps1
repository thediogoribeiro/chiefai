#requires -Version 7.4
BeforeAll { . "$PSScriptRoot/../common.ps1" }
Describe 'Fixed administrator configuration' {
    BeforeEach {
        $script:cfgRoot = Join-Path $TestDrive ([guid]::NewGuid().ToString('N'))
        [void](New-Item -ItemType Directory -Path $script:cfgRoot)
        $script:file = Join-Path $script:cfgRoot 'windows.json'
        $script:config = @{
            RepoPath = (Join-Path $script:cfgRoot 'repo'); AppHome = (Join-Path $script:cfgRoot 'runtime')
            SpringConfigPath = (Join-Path $script:cfgRoot 'config/application.yml')
            JavaPath = (Join-Path $script:cfgRoot 'java.exe'); GitPath = (Join-Path $script:cfgRoot 'git.exe')
            PsqlPath = (Join-Path $script:cfgRoot 'psql.exe'); PgIsReadyPath = (Join-Path $script:cfgRoot 'pg_isready.exe')
            JarPath = (Join-Path $script:cfgRoot 'runtime/chiefai.jar'); CandidateJar = 'target/candidate.jar'
            VersionFile = (Join-Path $script:cfgRoot 'runtime/commit.txt'); LogDir = (Join-Path $script:cfgRoot 'logs')
            StateDir = (Join-Path $script:cfgRoot 'state'); StagingDir = (Join-Path $script:cfgRoot 'staging')
            Remote = 'origin'; RemoteUrl = 'https://example.invalid/repo.git'; Branch = 'main'
            BackendTask = 'ChiefAI-Test-Backend'; SupervisorTask = 'ChiefAI-Test-Supervisor'; HealthUrl = 'http://127.0.0.1:1'
            DatabaseHost = '127.0.0.1'; DatabasePort = 5432; DatabaseName = 'test'; DatabaseUser = 'test'
            FetchTimeoutSeconds = 10; BuildTimeoutSeconds = 10; StopTimeoutSeconds = 10; StartTimeoutSeconds = 10; DatabaseTimeoutSeconds = 10
        }
    }
    It 'loads only the external fixed configuration without executing anything' {
        $script:config | ConvertTo-Json | Set-Content -LiteralPath $script:file
        (Get-ChiefConfig $script:file).Branch | Should -Be 'main'
    }
    It 'rejects embedded credentials without echoing them' {
        $script:config.RemoteUrl = 'https://test:secret-never-expose@example.invalid/repo.git'
        $script:config | ConvertTo-Json | Set-Content -LiteralPath $script:file
        try { Get-ChiefConfig $script:file; throw 'Expected rejection' }
        catch { $_.Exception.Message | Should -Not -Match 'secret-never-expose'; $_.Exception.Message | Should -Match 'credentials' }
    }
    It 'rejects runtime files within the source checkout' {
        $script:config.JarPath = Join-Path $script:config.RepoPath 'chiefai.jar'
        $script:config | ConvertTo-Json | Set-Content -LiteralPath $script:file
        { Get-ChiefConfig $script:file } | Should -Throw '*outside*'
    }
    It 'rejects a candidate path that escapes staging' {
        $script:config.CandidateJar = '../foreign.jar'
        $script:config | ConvertTo-Json | Set-Content -LiteralPath $script:file
        { Get-ChiefConfig $script:file } | Should -Throw '*relative path*'
    }
}
Describe 'Bounded substitute commands' {
    It 'checks a substitute exit code without exposing its output' {
        $exe = (Get-Process -Id $PID).Path
        { Invoke-ChiefCommand $exe @('-NoProfile', '-Command', "Write-Output 'secret-never-expose'; exit 7") $TestDrive 10 'Substitute' } | Should -Throw 'Substitute failed (exit code 7).'
    }
    It 'terminates only its own timed-out substitute child' {
        $exe = (Get-Process -Id $PID).Path
        { Invoke-ChiefCommand $exe @('-NoProfile', '-Command', 'Start-Sleep -Seconds 10') $TestDrive 1 'Substitute' } | Should -Throw '*timed out*'
    }
}
Describe 'Readiness without external HTTP' {
    It 'accepts an UP health response' {
        Mock Invoke-RestMethod { return [pscustomobject]@{status = 'UP'} }
        Wait-ChiefHealth ([pscustomobject]@{HealthUrl = 'http://example.invalid'; StartTimeoutSeconds = 1})
        Should -Invoke Invoke-RestMethod -Times 1
    }
    It 'fails within a bounded wait for a DOWN response' {
        Mock Invoke-RestMethod { return [pscustomobject]@{status = 'DOWN'} }
        { Wait-ChiefHealth ([pscustomobject]@{HealthUrl = 'http://example.invalid'; StartTimeoutSeconds = 1}) } | Should -Throw '*health check*'
    }
}
