# Módulos do ChiefAI

Este documento resume os módulos que existem atualmente no ChiefAI e o estado prático de cada um.

## Resumo rápido

| Módulo | Estado | Onde usar | O que faz |
| ------ | ------ | --------- | --------- |
| Visão geral | Ativo | `/` | Mostra resumo de finanças, saúde e connectors. |
| Finanças | Ativo | `/finance` | Mostra despesas guardadas, filtros, gráficos e exportação manual para Excel. |
| Saúde | Ativo | `/health` | Mostra snapshots e tendências de saúde. |
| Eventos/Manager | Ativo via API/webhooks | `POST /api/v1/events`, Telegram, WhatsApp | Normaliza mensagens e encaminha para agentes. |
| Agentes | Ativo | `GET /api/v1/agents` | Lista agentes configurados e ferramentas permitidas. |
| Tarefas | Ativo via API | `/api/v1/tasks` | Cria, lista, conclui e cancela tarefas pessoais. |
| Ações | Ativo via API | `/api/v1/actions` | Aprova ou rejeita ações propostas. |
| Sistema | Ativo via API/Manager e supervisor Windows | `/api/v1/system/*`, `/system` no Telegram | Estado, commit e atualizações controladas após aprovação. |
| Definições | Não ativo no frontend | rota reservada `/settings` | Módulo desativado na configuração atual. |

## Manager

Agente: `manager`

Função:

- classificar eventos;
- decidir que agente especialista deve tratar cada parte;
- pedir esclarecimentos quando faltarem dados.

Routing determinístico existente:

- eventos `OURA` vão para `health`;
- Gmail com etiqueta `ChiefAI/Gastos` vai para o fluxo pessoal/financeiro;
- `/health` vai para `health`;
- `/work` e `/rucodel` vão para `rucodel`;
- `/personal` vai para `personal`;
- `domainHint` válido tem prioridade.

Ferramentas permitidas:

- `internal.ask_clarification`
- `system.get_status`
- `system.get_version`
- `system.request_update`
- `system.get_update_status`

Ver [permissões](../agent-permissions.md), [sistema](../system-module.md) e [Windows](../windows-deployment.md). O módulo system é exclusivo do Manager; não é um subagente.

## Finanças

Agente relacionado: `personal`

Frontend:

```text
/finance
```

APIs principais:

- `GET /api/v1/finance/dashboard`
- `GET /api/v1/finance/expenses`
- `GET /api/v1/finance/expenses/{id}`
- `PATCH /api/v1/finance/expenses/{id}`
- `POST /api/v1/finance/expenses/{id}/approve`
- `POST /api/v1/finance/expenses/{id}/reject`
- `POST /api/v1/finance/expenses/{id}/retry`
- `GET /api/v1/finance/categories`
- `GET /api/v1/finance/expenses/export`

Entrada de despesas:

- Gmail com etiqueta `ChiefAI/Gastos`.

Fonte de verdade:

- PostgreSQL.

Exportação:

- manual para `.xlsx`;
- nunca automática para Sheets/Excel.

Ferramentas relacionadas:

- `gmail.read_labeled_expenses`
- `expenses.upsert`
- `expenses.generate_monthly_analysis`

## Saúde

Agente: `health`

Frontend:

```text
/health
```

APIs:

- `PUT /api/v1/health/snapshots/{date}`
- `GET /api/v1/health/snapshots/{date}`
- `GET /api/v1/health/snapshots`
- `GET /api/v1/health/trends`

Ferramentas permitidas:

- `oura.read_metrics`
- `health.save_metrics`
- `health.generate_report`
- `health.get_snapshots`
- `health.get_trends`

Nota: a análise de saúde é informativa e não constitui diagnóstico médico. O conector Oura existe, mas no MVP a ferramenta devolve erro funcional se a integração real não estiver ativa.

## Rucodel

Agente: `rucodel`

Contexto de conhecimento existente:

- RucoPI: métricas e acompanhamento da produção.
- RucoFi: informação financeira e processamento de documentos.
- RucoPlan: planeamento de produção e recolhas.

Comandos:

- `/rucodel`
- `/work`

Ferramentas permitidas:

- `teams.search_messages`
- `gmail.create_draft`
- `calendar.read_events`
- `trello.create_card`

Estado prático: Teams, Calendar e Trello estão presentes como ferramentas/adapters fake ou limitados no MVP. Ações externas podem exigir aprovação.

## Personal

Agente: `personal`

Responsável por:

- tarefas pessoais;
- assuntos privados;
- ingestão de despesas etiquetadas no Gmail;
- análise mensal objetiva de despesas.

Comando:

- `/personal`

Ferramentas permitidas:

- `personal_tasks.create`
- `personal_tasks.complete`
- `tasks.list`
- `calendar.read_events`
- `gmail.read_labeled_expenses`
- `expenses.upsert`
- `expenses.generate_monthly_analysis`
- `finance.get_dashboard`
- `finance.search_expenses`

As respostas usam a orquestração/outbox, sem ferramenta de envio disponibilizada ao modelo.

## Tarefas

APIs:

- `POST /api/v1/tasks`
- `GET /api/v1/tasks`
- `GET /api/v1/tasks/{id}`
- `POST /api/v1/tasks/{id}/complete`
- `POST /api/v1/tasks/{id}/cancel`

Estados:

- `OPEN`
- `IN_PROGRESS`
- `COMPLETED`
- `CANCELLED`

O dashboard ainda não tem secção própria para tarefas.

## Integrações

### Telegram

Webhook:

```text
POST /api/v1/integrations/telegram/webhook
```

Suporta texto e áudio. Quando `TELEGRAM_ENABLED=true`, valida o header `X-Telegram-Bot-Api-Secret-Token`.

### WhatsApp

Webhook:

```text
GET /api/v1/integrations/whatsapp/webhook
POST /api/v1/integrations/whatsapp/webhook
```

Suporta texto e áudio. Quando ativo, valida assinatura `X-Hub-Signature-256`.

### Gmail

Usado no fluxo financeiro para ler apenas mensagens com a etiqueta configurada:

```text
ChiefAI/Gastos
```

Não há varrimento completo da mailbox.

### Google Sheets

Connector genérico/futuro. Não é usado pelo fluxo financeiro atual.

### Google Calendar

Existe como connector/ferramenta de leitura (`calendar.read_events`). Não há criação real de eventos no MVP.

### Teams

Existe ferramenta `teams.search_messages`, mas no MVP devolve erro funcional quando integração real não está ativa.

### Trello

Existe ferramenta fake `trello.create_card`. Como escrita externa, pode exigir aprovação.

### Oura

Existe connector/ferramenta `oura.read_metrics`, mas a integração real não está completa neste MVP. Dados de saúde podem ser guardados via API de snapshots.

## Como adicionar um módulo ao frontend

O frontend usa uma lista de módulos exposta por:

```text
GET /api/v1/dashboard/modules
```

Para ativar um novo módulo:

1. Adicionar o módulo ao backend em `DashboardController`.
2. Adicionar rota no `FrontendController`.
3. Adicionar a vista correspondente em `src/main/resources/static/app/app.js`.
4. Manter o layout principal separado da lógica do módulo.

Não apresentar módulos desativados ou sem permissão.
