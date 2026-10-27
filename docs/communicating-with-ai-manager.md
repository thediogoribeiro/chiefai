# Comunicar Com O AI Manager

O AI Manager recebe mensagens, decide o tema e encaminha para um ou mais agentes especialistas.

```text
Mensagem ou áudio recebido
→ validação do canal e do utilizador
→ IncomingEvent normalizado
→ router determinístico ou Manager
→ agente especialista
→ resposta, registo ou ação proposta
→ outbox
→ resposta no canal de origem
```

Podes escrever em linguagem normal. Quando a mensagem mistura temas, por exemplo saúde e finanças, o Manager pode dividi-la por agentes e o ChiefAI devolve uma resposta curta com secções.

## Canais Disponíveis

- Web dashboard em `/`, `/finance` e `/health`.
- Telegram, através de `POST /api/v1/integrations/telegram/webhook`, quando `TELEGRAM_ENABLED=true`.
- Entrada manual por API em `POST /api/v1/events`.
- Gmail para despesas, lendo apenas emails com a etiqueta configurada `ChiefAI/Gastos`.

As perguntas financeiras e de saúde usam os dados guardados no ChiefAI/PostgreSQL. Não usam conversas, projetos ou memória do ChatGPT.

## Telegram

O bot Telegram é um canal privado para o utilizador OWNER. O webhook valida:

- `X-Telegram-Bot-Api-Secret-Token`;
- `from.id` e `chat.id` configurados para o OWNER.

Variáveis principais:

```env
TELEGRAM_ENABLED=true
TELEGRAM_BOT_TOKEN=
TELEGRAM_BOT_USERNAME=
TELEGRAM_WEBHOOK_SECRET=
CHIEFAI_TELEGRAM_OWNER_USER_ID=
CHIEFAI_TELEGRAM_OWNER_CHAT_ID=
```

O URL público do webhook tem de ser HTTPS e apontar para:

```text
/api/v1/integrations/telegram/webhook
```

Pode vir de um reverse proxy, túnel local ou infraestrutura própria. Configura esse URL junto do Telegram usando o token do bot fora do repositório.

## Comandos

| Comando | Destino | Exemplo |
| --- | --- | --- |
| `/start` | Telegram | Confirma ligação privada ao ChiefAI. |
| `/help` | Telegram | Mostra comandos disponíveis. |
| `/health <pergunta>` | Health Agent | `/health Como evoluiu o meu sono nos últimos 14 dias?` |
| `/finance <pergunta>` | Personal Agent com contexto financeiro | `/finance Quanto gastei este mês por categoria?` |
| `/rucodel <pergunta>` | Rucodel Agent | `/rucodel Resume os temas pendentes relacionados com a produção.` |
| `/work <pergunta>` | Rucodel Agent | `/work Que assuntos de produção estão pendentes?` |
| `/personal <pergunta>` | Personal Agent | `/personal Quais são as minhas tarefas abertas?` |

Texto sem comando vai para o Manager decidir:

```text
Como dormi esta semana e como estão os meus gastos?
```

## Ações Com Aprovação

Ferramentas internas de leitura podem ser usadas para responder com dados guardados. Ações externas, como Trello, Gmail, Calendar, Teams ou outras escritas externas, ficam como `ProposedAction` pendente de aprovação.

O webhook nunca executa diretamente ferramentas externas e nunca recebe credenciais.

## Limitações

- O perfil local usa fake LLM, fake transcription e connectors fake.
- Áudio continua compatível com o fluxo de transcrição configurado, mas esta alteração foca conversa textual.
- O Telegram limita mensagens a 4096 caracteres; o ChiefAI divide respostas longas.
- Saúde é informação não diagnóstica.

## Web

O dashboard exige login:

- `/login`
- `/finance`
- `/health`

Finanças mostra despesas, filtros, gráficos e exportação manual para Excel. Saúde mostra snapshots e tendências disponíveis.
