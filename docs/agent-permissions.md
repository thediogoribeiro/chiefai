# Permissões dos agentes

As instruções em `src/main/resources/agents/*/instructions.md` descrevem responsabilidades. A segurança é aplicada em Java: `ToolPolicyService` impõe o mapa de domínios, `AgentDefinitionLoader` valida a configuração, `AgentRunner` verifica propostas e `ToolExecutor` volta a validar antes da execução. O YAML não pode alargar o mapa Java.

| Agente | Módulos permitidos | Ferramentas permitidas |
| --- | --- | --- |
| manager | orchestration, system | `internal.ask_clarification`, `system.get_status`, `system.get_version`, `system.request_update`, `system.get_update_status` |
| rucodel | contexto profissional Rucodel via integrações | `teams.search_messages`, `gmail.create_draft`, `calendar.read_events`, `trello.create_card` |
| health | health | `oura.read_metrics`, `health.save_metrics`, `health.generate_report`, `health.get_snapshots`, `health.get_trends` |
| personal | task, expenses, informação pessoal | `personal_tasks.create`, `personal_tasks.complete`, `tasks.list`, `calendar.read_events`, `gmail.read_labeled_expenses`, `expenses.upsert`, `expenses.generate_monthly_analysis`, `finance.get_dashboard`, `finance.search_expenses` |

`calendar.read_events` mantém a leitura partilhada autorizada aos dois domínios que já a utilizavam. Não foi criado um módulo de negócio Rucodel vazio: esse especialista usa os adapters profissionais existentes. `system` é um módulo operacional exclusivo do Manager, não um quinto agente.

Só o Manager seleciona subagentes. Não existem ferramentas para um especialista chamar o Manager ou outro especialista. `RoutingService` filtra destinos do LLM; `/health`, `/personal`, `/rucodel`, `/work` e o `/finance` normalizado passam pelo mesmo `AgentRunner`. As permissões também se aplicam ao routing determinístico. O frontend e APIs OWNER continuam a chamar os seus casos de uso normais.

As respostas Telegram são controladas pela orquestração/outbox. Os modelos não recebem `telegram.reply` nem escolhem destinatários. Os conectores autenticam, normalizam e transportam dados; não decidem aprovações ou permissões de negócio.

`system.request_update` tem argumentos vazios e risco com aprovação obrigatória. Uma mensagem textual direta autenticada `/system update` ou um POST explícito com sessão OWNER cria a proposta. A confirmação usa a API de ações existente. Proveniência autenticada é uma coluna interna, não um campo de metadata que um cliente ou modelo possa preencher. Conteúdo de emails, áudio, páginas e mensagens reencaminhadas não concede autorização. A chave de API interna não substitui a sessão humana para propor/aprovar atualizações.

Nenhum agente recebe ferramentas de filesystem, shell, Git, execução de código ou edição do repositório. O supervisor é o único componente que faz atualizações, a partir da configuração fixa do administrador. Markdown sozinho não garante esta restrição. As permissões do sistema operativo continuam necessárias; ver [Windows](windows-deployment.md).
