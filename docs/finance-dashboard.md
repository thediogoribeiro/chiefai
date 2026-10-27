# Dashboard Financeiro

PostgreSQL é a fonte de verdade dos dados financeiros. O dashboard consome apenas APIs do backend.

## Endpoints

- `GET /api/v1/finance/dashboard`
- `GET /api/v1/finance/expenses`
- `GET /api/v1/finance/expenses/{id}`
- `PATCH /api/v1/finance/expenses/{id}`
- `POST /api/v1/finance/expenses/{id}/approve`
- `POST /api/v1/finance/expenses/{id}/reject`
- `POST /api/v1/finance/expenses/{id}/retry`
- `GET /api/v1/finance/categories`
- `GET /api/v1/finance/expenses/export`

## Filtros

Os endpoints suportam:

- `from`, `to`
- `owner`
- `category`
- `subcategory`
- `supplier`
- `status`
- `minAmount`, `maxAmount`
- `search`
- `page`, `size`, `sort` na tabela

O frontend reflete os filtros na query string.

## Exportação Excel

`GET /api/v1/finance/expenses/export` gera um `.xlsx` real via Apache POI. O ficheiro inclui:

- `Resumo`
- `Despesas`
- `Categorias`

O limite é configurável:

```env
FINANCE_EXPORT_MAX_ROWS=5000
```

Conteúdos que possam ser interpretados como fórmulas Excel são prefixados com `'`.

## Remoção do fluxo Sheets

O worker automático `ExpenseSpreadsheetSyncService` deixou de ser bean Spring e as ferramentas `google_sheets.*` saíram da allowlist do agente `personal`. Aprovar uma despesa já não cria `ProposedAction` para Sheets.
