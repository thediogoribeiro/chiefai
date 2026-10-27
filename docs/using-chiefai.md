# Usar o ChiefAI

O ChiefAI recebe informação, decide a que área pertence e encaminha para o agente certo.

```text
Mensagem/áudio/dado recebido
→ Manager
→ agente especialista
→ resposta, registo ou ação proposta
→ confirmação quando necessário
```

Podes escrever em linguagem normal. O Manager identifica o tema e chama um ou mais especialistas. Se a mensagem tiver assuntos misturados, pode ser dividida por agentes, por exemplo trabalho e saúde na mesma mensagem.

## Onde usar

A interface principal disponível é o dashboard web:

```text
http://localhost:8080/
```

O dashboard tem:

- `Visão geral`: resumo de módulos ativos.
- `Finanças`: despesas, filtros, gráficos e exportação manual para Excel.
- `Saúde`: métricas e tendências guardadas.

Para enviar mensagens ao Manager, a aplicação aceita eventos pela API `POST /api/v1/events` e webhooks de Telegram e WhatsApp quando configurados. Neste momento não existe uma caixa de chat no frontend.

## Como falar com o Manager

Podes enviar texto normal, por exemplo:

```text
Cria uma tarefa para rever os custos da fábrica.
Analisa o meu sono desta semana.
Marca no calendário uma consulta amanhã às 15h.
Resume esta reunião e prepara uma mensagem para o Teams.
```

Comandos suportados no início da mensagem:

- `/health`: envia diretamente para o agente de saúde.
- `/rucodel` ou `/work`: envia diretamente para o agente Rucodel.
- `/personal`: envia diretamente para o agente pessoal.

Exemplos:

```text
/health Como esteve o meu sono esta semana?
/rucodel Criar uma tarefa sobre a reunião de produção.
/personal Lembrar-me de comprar filtros.
```

Se o pedido não tiver dados suficientes, o Manager pode pedir esclarecimentos. Por exemplo, para calendário pode faltar data, hora ou duração. Neste MVP, o calendário existente é apenas de leitura/fake; ainda não há ferramenta implementada para criar eventos reais no calendário.

## Áudio

Os webhooks de Telegram e WhatsApp aceitam mensagens de áudio. O áudio é convertido para texto através do `TranscriptionGateway`.

Em local/teste, a transcrição é fake. Para transcrição real, é necessário configurar o gateway externo correspondente.

## Temas e agentes

| Tema | Agente | Como comunicar | Exemplo | Resultado esperado |
| ---- | ------ | -------------- | ------- | ------------------ |
| Encaminhamento geral | `manager` | Texto ou evento recebido pela API/webhook | “Reunião Rucodel e sono baixo esta semana.” | Divide o pedido e encaminha para `rucodel` e `health`. |
| Finanças pessoais | `personal` | Gmail com etiqueta `ChiefAI/Gastos` ou pedido textual sobre despesas | “Analisa os gastos deste mês.” | Usa dados guardados em PostgreSQL e devolve análise/encaminhamento. |
| Saúde | `health` | Dashboard, API de saúde, `/health`, dados Oura quando existirem | “Analisa o meu sono desta semana.” | Consulta snapshots e tendências. A resposta não é diagnóstico médico. |
| Rucodel | `rucodel` | `/rucodel`, `/work` ou texto sobre trabalho/Rucodel | “Resume esta reunião da Rucodel.” | Usa contexto Rucodel e pode propor ações como Trello/Telegram conforme políticas. |
| Tarefas pessoais | `personal` | `/personal`, texto normal ou API de tarefas | “Cria uma tarefa para rever os custos da fábrica.” | Cria tarefa interna quando a ferramenta autorizada for usada. |
| Telegram | integração | Webhook configurado | Mensagem de texto ou áudio no Telegram | Cria evento `TELEGRAM`; pode enviar resposta para outbox Telegram. |
| WhatsApp | integração | Webhook configurado | Mensagem de texto ou áudio WhatsApp | Cria evento `WHATSAPP`; integração desativada por defeito. |
| Gmail despesas | integração | Email com etiqueta `ChiefAI/Gastos` | Fatura/recibo etiquetado no Gmail | Cria despesa idempotente se o documento for válido. |
| Oura/saúde | integração | Dados guardados via módulo de saúde; connector Oura fake no MVP | Snapshot Oura | Aparece no dashboard de saúde quando persistido. |
| Teams/Trello/Calendar | integrações | Ferramentas fake/desativadas no MVP | “Preparar cartão Trello.” | Pode gerar resultado fake ou erro funcional quando integração real não está ativa. |

## Finanças

O fluxo financeiro implementado é:

1. Reencaminhar ou receber a fatura/recibo no Gmail ligado ao ChiefAI.
2. Aplicar a etiqueta `ChiefAI/Gastos`.
3. A aplicação processa apenas emails com essa etiqueta.
4. A despesa é guardada em PostgreSQL.
5. A despesa aparece no dashboard `Finanças`.
6. Despesas ambíguas ficam em revisão.
7. A exportação para Excel é manual no frontend.

Não existe endereço de email público próprio da aplicação. O método suportado é usar a conta Gmail ligada e a etiqueta configurada.

### Titulares aceites

O sistema reconhece estes titulares:

- `DIOGO`: José Diogo Ribeiro, Diogo Ribeiro, José Diogo Cabral Castelo Branco.
- `MARIA`: Maria João Pacheco Pontes, Maria Pontes.

Se a fatura estiver em nome de outra pessoa, fica como `OTHER` e é ignorada. Se o titular não for claro, fica como `UNKNOWN` e entra em revisão.

### Consultar e filtrar despesas

No dashboard, abre:

```text
/finance
```

Podes filtrar por:

- data inicial e final;
- titular;
- categoria;
- subcategoria;
- fornecedor;
- estado;
- valor mínimo e máximo;
- pesquisa livre.

Os filtros ficam na URL, por isso podes refrescar ou partilhar a mesma vista.

### Revisão

No dashboard financeiro, despesas com revisão pendente aparecem destacadas. A interface permite ver detalhe, aprovar e repetir uma falha.

Também existem endpoints para operações avançadas:

- `PATCH /api/v1/finance/expenses/{id}`: corrige titular, categoria, subcategoria ou descrição.
- `POST /api/v1/finance/expenses/{id}/approve`: aprova.
- `POST /api/v1/finance/expenses/{id}/reject`: rejeita.
- `POST /api/v1/finance/expenses/{id}/retry`: volta a colocar uma falha em revisão.

Correção de valor monetário ainda não está implementada.

### Exportar para Excel

Na secção `Finanças`, usa o botão `Exportar para Excel`.

A exportação:

- respeita os filtros atuais;
- gera um ficheiro `.xlsx`;
- inclui `Resumo`, `Despesas` e `Categorias`;
- não envia nada automaticamente para Google Sheets ou Excel.

## Saúde

Abre:

```text
/health
```

A secção mostra os dados de saúde guardados, quando existirem:

- Sleep Score;
- Readiness Score;
- Activity Score;
- HRV;
- frequência cardíaca em repouso;
- duração do sono;
- tendências.

Quando não há dados, o dashboard mostra estado vazio. A análise é informativa e não constitui diagnóstico médico.

## Tarefas

O módulo de tarefas existe através da API e das ferramentas do agente `personal`.

Endpoints disponíveis:

- `POST /api/v1/tasks`
- `GET /api/v1/tasks`
- `GET /api/v1/tasks/{id}`
- `POST /api/v1/tasks/{id}/complete`
- `POST /api/v1/tasks/{id}/cancel`

O dashboard ainda não tem uma página própria de tarefas.

## Ações e confirmações

Ações externas ou com risco podem ficar pendentes de aprovação em:

```text
GET /api/v1/actions
POST /api/v1/actions/{id}/approve
POST /api/v1/actions/{id}/reject
```

O fluxo financeiro atual não cria ações para enviar despesas para Google Sheets. As despesas ficam no PostgreSQL e são vistas no dashboard.

## Limitações atuais

- Não há chat no frontend.
- Google Sheets existe como connector genérico/futuro, mas não é usado no fluxo financeiro.
- Calendar é leitura/fake no MVP; não cria eventos reais.
- Teams, Trello e Oura têm fakes/adapters limitados quando integrações reais não estão configuradas.
- OCR/vision para imagens de faturas ainda não está implementado.
- Em local/teste, OpenAI e transcrição usam fakes por defeito.
