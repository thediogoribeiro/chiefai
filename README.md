# ChiefAI / Personal Agent Core

Monólito modular em Java 21/Spring Boot para receber eventos, normalizá-los, encaminhá-los para agentes lógicos e disponibilizar um dashboard central do ChiefAI.

O PostgreSQL é a fonte de verdade dos dados financeiros. Google Sheets/Excel já não fazem parte do fluxo automático de despesas; Excel existe apenas como exportação manual a partir do dashboard.

## Arquitetura

- `ingestion`: entrada idempotente de eventos normalizados.
- `orchestration`: routing determinístico ou manager LLM.
- `agent`: definições, instruções, knowledge retrieval e runner.
- `action`: ferramentas, allowlists, aprovação e execução.
- `expenses`: ingestão Gmail, extração, revisão, aprovação e APIs financeiras.
- `dashboard`: módulos do dashboard e frontend servido pelo backend.
- `health`: snapshots e tendências objetivas de saúde.
- `integration`: ports/adapters para OpenAI, Google, Telegram, WhatsApp, Teams, Trello e Oura.

Frontend estático:

```text
src/main/resources/static/
├── index.html
└── app/
    ├── app.js
    └── styles.css
```

## Pré-requisitos

- Java 21 ou superior.
- Docker com Docker Compose.
- Bash em macOS ou Linux.

## Configuração local

```bash
cp .env.example .env
```

Os valores locais são apenas para desenvolvimento. Não coloques segredos reais no repositório, Markdown ou logs.

## Iniciar backend e frontend

```bash
./scripts/start-backend.sh
```

Depois abre:

```text
http://localhost:8080/
```

Healthcheck:

```bash
curl http://localhost:8080/actuator/health
```

## Guias de utilização

- [Usar o ChiefAI](docs/using-chiefai.md): guia simples para comunicar com o Manager, usar o dashboard, finanças, saúde e tarefas.
- [Módulos do ChiefAI](docs/modules/README.md): resumo dos módulos, agentes, integrações e estado atual de cada área.
- [Comunicar com o AI Manager](docs/communicating-with-ai-manager.md): Telegram, comandos, routing e ações pendentes.
- [Controlo de acesso](docs/access-control.md): login OWNER, hash BCrypt e autorização Telegram.

## Testes

```bash
./scripts/run-tests.sh
./mvnw verify
```

Os testes usam PostgreSQL real, sem H2 e sem chamadas reais a OpenAI, Gmail, Google Sheets, Telegram, WhatsApp, Teams, Calendar, Trello ou Oura.

## Endpoints principais

- `GET /`
- `GET /login`
- `GET /finance`
- `GET /health`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/me`
- `GET /api/v1/dashboard/modules`
- `GET /api/v1/dashboard/overview`
- `GET /api/v1/finance/dashboard`
- `GET /api/v1/finance/expenses`
- `GET /api/v1/finance/expenses/{id}`
- `PATCH /api/v1/finance/expenses/{id}`
- `POST /api/v1/finance/expenses/{id}/approve`
- `POST /api/v1/finance/expenses/{id}/reject`
- `POST /api/v1/finance/expenses/{id}/retry`
- `GET /api/v1/finance/categories`
- `GET /api/v1/finance/expenses/export`
- `POST /api/v1/events`
- `GET /api/v1/health/snapshots`
- `GET /api/v1/health/trends`
- `POST /api/v1/integrations/telegram/webhook`

## Fluxo de despesas

```text
Gmail
→ etiqueta ChiefAI/Gastos
→ extração da fatura/despesa
→ validação e deduplicação
→ PostgreSQL
→ APIs do backend
→ dashboard ChiefAI
```

Não há envio automático de despesas para Google Sheets ou Excel. A exportação manual gera `.xlsx`:

```bash
curl -OJ 'http://localhost:8080/api/v1/finance/expenses/export?from=2026-09-01&to=2026-09-30'
```

## OpenAI

Por defeito `OPENAI_ENABLED=false`, usando fake LLM determinístico. A API não herda automaticamente conversas, memória, projetos ou autenticação do ChatGPT.

## Acesso

O dashboard e APIs privadas usam um único utilizador `OWNER`. Configura um ficheiro externo com `CHIEFAI_ACCESS_FILE` ou variáveis `CHIEFAI_OWNER_USERNAME` e `CHIEFAI_OWNER_PASSWORD_HASH`. Em produção, a aplicação falha no arranque se a configuração OWNER/Telegram estiver incompleta.

O Telegram é privado por `from.id` e `chat.id`; o número de telefone não é recebido automaticamente pelo bot.

## Limitações do MVP

- Google OAuth tem persistência cifrada e endpoints, mas as chamadas reais completas às APIs Google continuam isoladas como trabalho seguinte.
- Google Sheets existe como connector genérico/futuro, mas não é usado pelo fluxo financeiro.
- OCR/vision para imagens de faturas não está implementado; email e PDF são suportados.
- O frontend usa polling controlado para atualização, não WebSocket/SSE.
- A análise de saúde é informativa e não constitui diagnóstico médico.
