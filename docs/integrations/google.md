# Integração Google

Gmail, Google Sheets e Google Calendar partilham a mesma autorização OAuth do utilizador. Não uses service account para Gmail pessoal.

## Variáveis

```env
GOOGLE_ENABLED=false
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
GOOGLE_REDIRECT_URI=http://localhost:8080/api/v1/integrations/google/callback
GOOGLE_TOKEN_ENCRYPTION_KEY=
GOOGLE_EXPENSES_SPREADSHEET_ID=
GOOGLE_EXPENSES_RAW_SHEET=Despesas
GOOGLE_EXPENSES_REVIEW_SHEET=Revisao
GOOGLE_EXPENSES_DASHBOARD_SHEET=Dashboard
GOOGLE_EXPENSES_MONTHLY_SHEET=Resumo Mensal
```

`GOOGLE_TOKEN_ENCRYPTION_KEY` deve ser um segredo forte e diferente por ambiente. Nunca o coloques em Git.

## OAuth

1. Cria uma app OAuth no Google Cloud.
2. Configura o redirect URI para `http://localhost:8080/api/v1/integrations/google/callback` em local.
3. Ativa scopes de Gmail modify, Spreadsheets e Calendar events.
4. Inicia autorização:

```bash
curl -X POST http://localhost:8080/api/v1/integrations/google/authorize
```

5. Abre o `authorizationUrl`, conclui consentimento e confirma estado:

```bash
curl http://localhost:8080/api/v1/integrations/google/status
```

## Estado do MVP

O código já inclui endpoints, entidade de token cifrado, validação de configuração, ports e fakes. Os adapters reais estão isolados atrás das interfaces, mas a troca real de `code` por refresh token e as chamadas completas às APIs Gmail/Sheets/Calendar ainda estão marcadas como trabalho seguinte. Enquanto isso, local/test usam fakes funcionais.

## Gmail despesas

A aplicação deve ler apenas mensagens com a etiqueta configurada:

```env
GMAIL_EXPENSES_INPUT_LABEL=ChiefAI/Gastos
GMAIL_EXPENSES_PROCESSED_LABEL=ChiefAI/Processado
GMAIL_EXPENSES_REVIEW_LABEL=ChiefAI/Revisao
```

Depois de processar, adiciona `ChiefAI/Processado` ou `ChiefAI/Revisao`. O email original não é apagado.

## Google Sheets

Google Sheets permanece como connector genérico/futuro, mas já não é usado pelo fluxo financeiro. As despesas são guardadas em PostgreSQL e consultadas pelo dashboard ChiefAI.

Para finanças, a saída suportada é exportação manual `.xlsx` através de:

```text
GET /api/v1/finance/expenses/export
```
