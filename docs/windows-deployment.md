# ChiefAI num PC Windows

## 1. Dependências e disponibilidade

Os scripts requerem **PowerShell 7.4+ (`pwsh.exe`)**, Windows com Task Scheduler, **JDK 21**, Git e PostgreSQL com os clientes `psql.exe` e `pg_isready.exe`. Windows PowerShell 5.1 não é suportado. O Maven Wrapper do repositório descarrega Maven/dependências se necessário; não é preciso instalar Maven separadamente.

Instala JDK 21 e Git pelos respetivos instaladores e regista os caminhos absolutos. Instala PostgreSQL, por exemplo 16, como serviço Windows com arranque automático, ou usa um servidor PostgreSQL já existente e acessível. Cria a base `personal_agent` e uma conta própria, sem privilégios de superuser; Flyway precisa de criar/alterar o schema da aplicação. Hibernate continua apenas a validar.

O `docker-compose.yml` existente gere PostgreSQL para desenvolvimento. Os scripts Windows **não iniciam Docker Compose nem apagam volumes**: aguardam a base configurada. Docker Desktop pode depender da sessão do utilizador e não é uma garantia de PostgreSQL antes do login. Para arranque sem sessão, prefere PostgreSQL como serviço Windows ou um servidor acessível. Se usares Compose, configura e verifica explicitamente um engine disponível nesse cenário.

Os **builds de atualização executam os testes Testcontainers**, que precisam de um engine Docker acessível à conta do supervisor e da imagem PostgreSQL. Pode ser um engine remoto/headless. Se usares apenas Docker Desktop, as atualizações podem precisar de sessão iniciada. Sem Docker, a validação falha antes da paragem, mantendo a aplicação atual. Os testes não usam a base de produção como fallback.

Um PC desligado, suspenso, hibernado ou sem rede não executa o serviço nem recebe eventos. Nas opções de energia, configura suspensão/hibernação de acordo com o cenário de serviço, mantém alimentação e rede e verifica o comportamento ao fechar a tampa num portátil. Não é prometida disponibilidade 24/7, wake-on-LAN ou recuperação de falhas de energia.

## 2. Contas e diretórios

Prepara diretórios locais, fora de pastas sincronizadas:

```text
C:\ChiefAI\source       checkout Git
C:\ChiefAI\config       windows.json, application.yml, ficheiro OWNER, pgpass se utilizado
C:\ChiefAI\runtime      chiefai.jar, commit.txt e backup do JAR
C:\ChiefAI\state        identidade do processo, sinal de paragem e locks
C:\ChiefAI\logs         logs com limites
C:\ChiefAI\staging      builds candidatos temporários
```

Usa contas Windows dedicadas, com password e direito **Log on as a batch job**, sem login administrativo para a execução normal. O instalador exige Administrator apenas para registar/delegar as tarefas. A documentação Microsoft descreve os parâmetros de [Register-ScheduledTask](https://learn.microsoft.com/en-us/powershell/module/scheduledtasks/register-scheduledtask?view=windowsserver2025-ps).

Preferencialmente usa duas contas:

| Conta | Permissões necessárias |
| --- | --- |
| Backend | leitura de Java, JAR, scripts e configuração; escrita em `state` e logs do backend; acesso à base e aos segredos das integrações |
| Supervisor | Git em `source`, leitura/escrita em `staging` e `runtime`, sinal/locks em `state`, log do supervisor, leitura da configuração; SELECT de Flyway e SELECT/UPDATE de `system_update`; iniciar/consultar a tarefa Backend |

O instalador delega leitura/execução apenas na tarefa Backend ao supervisor. O sinal de paragem é processado pelo wrapper da conta Backend, que verifica o seu próprio processo. Não precisa de permissões para terminar processos de outras contas. `status-chiefai.ps1` consulta CIM: executa-o como Backend ou como operador com permissão para consultar o processo. Restringe ACLs da configuração e dos scripts ao administrador/supervisor; o Backend não deve poder editar `source`, `staging`, o JSON administrativo ou o JAR. Pode usar-se uma única conta dedicada se necessário, mas nesse caso não há isolamento de filesystem entre backend e supervisor. Não uses `SYSTEM` para evitar permissões excessivas.

As permissões de escrita em `state` permitem sinalizar a própria instância ChiefAI; não dão uma ferramenta de processos ao LLM. Os scripts nunca procuram todos os processos Java para os terminar.

## 3. Preparar código e configuração

Clona o repositório pretendido para `source`, seleciona a branch autorizada e confirma `git status` limpo. Só esse remoto/URL e branch serão usados nas atualizações. Para repositórios privados, configura SSH ou o credential manager da conta Supervisor com acesso de leitura; não coloques tokens no URL. Nenhum script faz push.

Copia `scripts/windows/config.example.json` para `C:\ChiefAI\config\windows.json`. Ajusta todos os caminhos, remoto, URL esperado, branch, nomes das tarefas, base, artefacto e timeouts. O JSON não contém passwords. Configuração, logs, estado, staging e runtime têm de ficar fora do checkout.

Cria `C:\ChiefAI\config\application.yml`, por exemplo:

```yaml
server:
  address: 127.0.0.1
  port: 8080
personal-agent:
  access:
    file: C:/ChiefAI/config/owner-access.yml
```

Usa o formato OWNER existente em `src/main/resources/access/owner-access.example.yml`, com hash BCrypt. Configura `POSTGRES_PASSWORD` e os segredos OpenAI/Telegram/Google no ambiente da **conta Backend**, ou injeta-os através do mecanismo de segredos já utilizado na máquina. A chave OpenAI é apenas ambiente/secret store. `.env` não é carregado automaticamente pelos scripts Windows. Não guardes segredos em Git ou no histórico de comandos. Ao alterar variáveis persistidas, confirma que o ambiente das tarefas foi renovado (pode ser necessário reiniciar a sessão/serviço ou o PC).

A conta Supervisor precisa de autenticação PostgreSQL. Usa `PGPASSFILE` num ficheiro externo protegido por ACL, ou o ambiente `POSTGRES_PASSWORD`/`PGPASSWORD` da conta; os parâmetros não passam passwords na linha de comandos. Host, porta, base e utilizador vêm do JSON. O build candidato remove variáveis de integração, base de dados e configuração Spring do ambiente herdado. Não depende de credenciais de produção para os testes.

Em produção, o perfil Spring existente exige HTTPS e cookies secure. Configura TLS/reverse proxy e o `HealthUrl` compatível e de confiança; não desatives a validação de certificados. O URL local HTTP do exemplo destina-se a uma instalação local. O endpoint `/actuator/health` inclui a verificação da base de dados; se escolheres um grupo readiness próprio, inclui também `db` nesse grupo. Telegram precisa de webhook público HTTPS e secret, com IDs OWNER configurados. Ver [controlo de acesso](access-control.md) e [OpenAI](openai-configuration.md).

Para a instalação inicial, numa consola configurada com JDK 21 e Docker para testes:

```powershell
Set-Location C:\ChiefAI\source
.\mvnw.cmd --batch-mode verify
if ($LASTEXITCODE -ne 0) { throw 'Build/testes falharam' }
Copy-Item .\target\personal-agent-core-0.1.0-SNAPSHOT.jar C:\ChiefAI\runtime\chiefai.jar
$commit = git rev-parse HEAD
if ($LASTEXITCODE -ne 0) { throw 'Não foi possível ler o commit' }
Set-Content C:\ChiefAI\runtime\commit.txt -Value $commit -Encoding ascii -NoNewline
```

O nome `CandidateJar` tem de acompanhar alterações futuras à versão/nome do artefacto Maven. O supervisor valida o ZIP, a classe principal ChiefAI e o manifest executável antes da troca.

## 4. Instalar e operar

Numa consola **PowerShell 7 como Administrator**, após rever a configuração e as ACLs:

```powershell
C:\ChiefAI\source\scripts\windows\install-startup.ps1 -ConfigPath C:\ChiefAI\config\windows.json
```

O instalador pede as credenciais Backend e Supervisor e regista duas tarefas AtStartup, com logon por password e execução limitada, sem depender de sessão interativa. Não inicia as tarefas durante a instalação. Verifica políticas de domínio e o resultado numa reinicialização controlada; passwords alteradas exigem atualizar a credencial da tarefa. Os scripts devem estar autorizados pela política de execução da organização.

Arranque manual das tarefas instaladas:

```powershell
Start-ScheduledTask -TaskName ChiefAI-Backend
Start-ScheduledTask -TaskName ChiefAI-Supervisor
```

O wrapper define o diretório de trabalho, usa o JAR/configuração absolutos, verifica JDK 21, Git, artefactos e PostgreSQL, e aguarda a base até ao timeout. Fica ativo a supervisionar exclusivamente o processo que lançou. O supervisor é outra tarefa/processo e continua quando o backend termina.

```powershell
C:\ChiefAI\source\scripts\windows\status-chiefai.ps1 -ConfigPath C:\ChiefAI\config\windows.json
C:\ChiefAI\source\scripts\windows\stop-chiefai.ps1 -ConfigPath C:\ChiefAI\config\windows.json
```

A paragem usa um sinal com o identificador da instância. O wrapper confirma executável Java, JAR, marcador aleatório e criação do processo antes de o terminar. Se não puder verificar a identidade, falha sem terminar outros processos. A paragem Windows termina o processo identificado; não é um endpoint genérico de execução. Para voltar a iniciar, usa `Start-ScheduledTask`. Também podes executar `start-chiefai.ps1` diretamente com a conta Backend para diagnóstico; esse comando fica em foreground.

O status devolve exit code 1 quando não há processo ou ocorre erro; os restantes scripts falham com mensagem e exit code não zero quando uma operação falha. Resultados de atualização são registados na base, enquanto o supervisor permanece ativo. Uma paragem manual do backend não para a fila/supervisor; não aproves atualizações durante manutenção.

Logs: `chiefai.log` roda diariamente e a cada 10 MB, com histórico de 14 dias e teto de arquivo de 200 MB; `supervisor.log` tem quatro arquivos de 5 MB. O launcher mantém um erro atual e um anterior e termina o seu backend se o erro ultrapassar 10 MB. Não são guardados prompts completos ou segredos pelo mecanismo operacional. Código de saída de Git, build, psql e scripts é verificado; stderr desses comandos não é copiado para o resultado da atualização.

## 5. Pedir e confirmar atualização

No Telegram autenticado do OWNER, envia exatamente:

```text
/system update
```

O Manager cria uma proposta e devolve o UUID da ação. Mensagens reencaminhadas, áudio, texto de email, metadata ou instruções embebidas em conteúdo não autorizam o pedido. Não envies URLs, branches ou comandos. Também podes criar uma proposta com `POST /api/v1/system/update-proposals`, sem argumentos, usando sessão humana OWNER.

A confirmação pelo Telegram **ainda não está implementada**. Faz login no dashboard como OWNER. Podes usar a consola do browser na origem do ChiefAI para rever e aprovar pela API existente:

```javascript
const me = await fetch('/api/v1/auth/me').then(r => r.json());
const actions = await fetch('/api/v1/actions?status=PENDING_APPROVAL').then(r => r.json());
console.table(actions.map(a => ({id: a.id, tool: a.toolName, reason: a.reason})));
// Depois de rever, substitui pelo UUID concreto que pretendes aprovar:
const actionId = 'UUID-DA-ACAO';
await fetch(`/api/v1/actions/${actionId}/approve`, {
  method: 'POST', headers: {'X-XSRF-TOKEN': me.csrfToken}
}).then(async r => { if (!r.ok) throw new Error(await r.text()); return r.json(); });
```

Para criar a proposta pela mesma sessão:

```javascript
await fetch('/api/v1/system/update-proposals', {
  method: 'POST', headers: {'X-XSRF-TOKEN': me.csrfToken}
}).then(r => r.json());
```

A chave interna de API não pode propor/aprovar atualizações. A proposta fica pendente até confirmação explícita. O worker de ações enfileira-a transacionalmente; só depois o supervisor externo a pode reclamar.

## 6. Resultado, falhas e recuperação

Consulta `/system status`, `/system version` e `/system updates` no Telegram ou os endpoints `/api/v1/system/status`, `/version`, `/updates/latest` e `/updates/{id}` na sessão OWNER. A ação e o pedido têm UUIDs diferentes; o resultado contém `actionId` para correlação. O resultado é enviado pelo outbox ao Telegram OWNER quando configurado e o backend estiver operacional.

O supervisor recusa alterações versionadas, staged, conflitos, remoto divergente, branch errada ou histórico sem fast-forward. Ficheiros locais não versionados que colidam com a nova revisão fazem o merge falhar; não há reset, clean ou sobrescrita forçada. Sem novo commit instalado, regista `NO_CHANGES` sem reiniciar.

A candidata é clonada para staging e passa `mvnw.cmd --batch-mode verify` antes da paragem. Falha de fetch, build, testes ou JAR deixa a versão anterior em execução (`FAILED_PRE_SWITCH`). Após validação, o wrapper para o backend, o checkout avança por fast-forward e o JAR é trocado com backup. O supervisor inicia a tarefa Backend e aguarda readiness UP, com timeout.

Se o arranque/readiness falhar, para a candidata e compara o fingerprint Flyway com o anterior. Com schema idêntico e sem alterações dos ficheiros de migrations na candidata, tenta restaurar JAR e commit anteriores e inicia-os (`FAILED_START` mesmo que a recuperação tenha sucesso). Se os ficheiros de migrations ou o estado Flyway mudaram, a base está inacessível ou a recuperação falha, regista `ROLLBACK_FAILED` e preserva o backup para intervenção manual. **Restaurar um JAR não desfaz migrations**. O histórico Flyway por si só não prova que uma operação não transacional não teve efeitos; candidatas com migrations alteradas exigem recuperação manual em caso de falha de arranque, mesmo que o fingerprint coincida. Revê e testa migrations versionadas quanto a compatibilidade antes de as publicar; nunca é o LLM que decide executar SQL destrutivo.

Um checkout já avançado não é recuado à força após rollback; `commit.txt` identifica o JAR realmente instalado. Um novo pedido pode voltar a validar a mesma candidata. Após crash/reboot a meio de RUNNING, o supervisor regista intervenção manual em vez de repetir uma troca às cegas. Antes de retomar, verifica JAR, `commit.txt`, estado Flyway e logs. Não apagues a base nem volumes para recuperar. O supervisor não recupera dados através de down migrations.

## 7. Testes isolados e validação Windows

Num Windows com PowerShell 7.4+, Git e Pester 5.7+:

```powershell
Install-Module Pester -MinimumVersion 5.7.1 -Scope CurrentUser
Invoke-Pester C:\ChiefAI\source\scripts\windows\tests -Output Detailed
```

Os testes criam repositórios Git temporários locais e JARs substitutos e fazem mock de base de dados, Task Scheduler, build, readiness e processos. Não param serviços reais, não instalam tarefas e não contactam GitHub/OpenAI/Telegram. Verificam ausência de alterações, trabalho local/staged, remoto, falha de build/JAR, troca, rollback seguro, migrations incompatíveis, locks e identidade do processo.

Os testes Java executam-se com `mvnw.cmd verify`, usando PostgreSQL Testcontainers e gateways fake/mocks. Em Docker que exija API 1.44+, o docker-java desta versão pode precisar de `JAVA_TOOL_OPTIONS=-Dapi.version=1.44` no ambiente de teste/supervisor. No ambiente de implementação foi necessário esse ajuste.

O ambiente de implementação foi macOS: análise sintática e testes de funções PowerShell com mocks não validam Task Scheduler, CIM, ACLs, contas nem arranque antes do login num Windows real. Esses cenários exigem uma máquina Windows de teste, com reboot controlado, incluindo PostgreSQL indisponível, duplicação de tarefas e credenciais sem privilégios administrativos.

### Validação realizada nesta implementação (2026-10-27)

- macOS 14.8.7, JDK 21.0.13: `./mvnw verify` passou com 55 testes, zero falhas e zero ignorados; PostgreSQL 16 isolado em Testcontainers e HTTP OpenAI/Telegram fake ou mock.
- O comando Maven usou `TESTCONTAINERS_RYUK_DISABLED=true` e `JAVA_TOOL_OPTIONS=-Dapi.version=1.44` neste Docker Desktop 29.7.2. Os containers de teste foram encerrados explicitamente pelos testes; estas variáveis não são uma recomendação para desativar Ryuk noutras instalações.
- PowerShell 7.4.20 e Pester 5.7.1 numa pasta temporária: análise sintática de todos os scripts e **22 testes Pester passados**, zero falhas e zero ignorados, com repositórios locais, processos substitutos e mocks.
- Não foram executados instalação de tarefas, arranque/paragem de serviços ChiefAI reais, atualizações no checkout da aplicação ou validação num Windows real.
