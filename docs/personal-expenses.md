# Despesas Pessoais

Este módulo processa faturas/recibos recebidos no Gmail pessoal com a etiqueta `ChiefAI/Gastos`.

## Como usar

1. Cria no Gmail a etiqueta `ChiefAI/Gastos`.
2. Reencaminha a fatura/recibo para a conta ligada.
3. Aplica a etiqueta `ChiefAI/Gastos`.
4. A aplicação lê apenas essa etiqueta, extrai e guarda a despesa em PostgreSQL.
5. Revê/aprova/corrige no dashboard ChiefAI.
6. Usa `Exportar para Excel` no dashboard quando quiseres descarregar um `.xlsx`.

## Titulares reconhecidos

- `DIOGO`: José Diogo Ribeiro, Diogo Ribeiro, José Diogo Cabral Castelo Branco.
- `MARIA`: Maria João Pacheco Pontes, Maria Pontes.
- `OTHER`: outro titular; a despesa é ignorada.
- `UNKNOWN`: titular incerto; vai para revisão.

## Estados

- `RECEIVED`
- `EXTRACTED`
- `IGNORED_OTHER_OWNER`
- `REVIEW_REQUIRED`
- `APPROVED`
- `FAILED`
- `SYNC_PENDING` e `SYNCED` existem apenas por compatibilidade com dados legados.

## Idempotência

A chave usa `gmailMessageId + attachmentPartId + contentHash`. Reprocessar o mesmo email/anexo não cria nova despesa.

## Dashboard

Os totais, gráficos, comparação com período anterior, fornecedores e agregações são calculados no backend com `BigDecimal` e timezone `Europe/Lisbon`.

## Excel

A exportação é manual, respeita os filtros atuais e gera `.xlsx` com separadores `Resumo`, `Despesas` e `Categorias`. Conteúdos iniciados por `=`, `+`, `-` ou `@` são escapados para evitar formula injection.

## Limitações

- Não guarda PDFs nem corpo integral do email na base de dados.
- OCR/vision para imagens ainda não está implementado.
- Faturas sem titular claro entram em revisão.
- Categorias sem regra dependem do fake/LLM e devem ser revistas quando a confiança for baixa.
