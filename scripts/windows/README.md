# Operação Windows

Requer **PowerShell 7.4 ou superior** (`pwsh.exe`), Windows com Task Scheduler, JDK 21, Git e ferramentas PostgreSQL (`psql`, `pg_isready`). Windows PowerShell 5.1 não é suportado.

Segue [o guia completo](../../docs/windows-deployment.md). Copia `config.example.json` para um diretório externo ao checkout, ajusta os caminhos e configura segredos na conta Windows, nunca neste JSON.

- `install-startup.ps1`: instala duas tarefas de arranque com contas dedicadas; exige Administrator. Não inicia as tarefas automaticamente.
- `start-chiefai.ps1`: wrapper persistente do backend, usado pela tarefa; valida pré-requisitos, aguarda PostgreSQL e regista a identidade do processo.
- `stop-chiefai.ps1`: pede ao wrapper que pare exclusivamente o processo identificado.
- `status-chiefai.ps1`: consulta PID, commit e readiness.
- `update-chiefai.ps1`: supervisor persistente externo. Consome apenas pedidos aprovados na tabela `system_update`. Não recebe URL, branch ou comandos como argumentos.

Todos recebem `-ConfigPath C:\ChiefAI\config\windows.json`. Iniciar manualmente: `Start-ScheduledTask -TaskName ChiefAI-Backend`. O wrapper fica em execução até o backend terminar.

Os testes Pester em `tests/` usam Git local temporário e substituem build, base de dados, paragem e arranque. Não instalam tarefas nem contactam serviços reais. Ver comandos no guia.
