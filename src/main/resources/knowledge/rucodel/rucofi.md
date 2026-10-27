# RucoFi — domínio financeiro e processamento documental

## Objetivo

RucoFi recebe documentos financeiros, classifica-os, extrai e valida os dados, evita duplicados e suporta reconciliação de faturas e recibos. Os PDFs originais não ficam guardados permanentemente no servidor; persistem dados extraídos, referências, evidência e resultado das validações.

## Fluxo de importação

1. Aceitar vários ficheiros e mostrar progresso por ficheiro.
2. Calcular impressão para deduplicação técnica.
3. Classificar como `INVOICE`, `RECEIPT` ou `UNSUPPORTED` antes de selecionar parser e antes de persistir dados de negócio.
4. Aplicar o parser correto, validar e gravar resultado.
5. Encaminhar documentos incompletos ou inconsistentes para revisão humana.

Faturas e recibos podem vir misturados no mesmo upload. Nunca aplicar o parser de fatura a um recibo, ou vice-versa.

## Identidade

Cliente único por `número de cliente + NIF`. O nome é descritivo; dois clientes com o mesmo NIF mas códigos diferentes continuam distintos (caso dos códigos 59 e 60).

Uma fatura guarda, no mínimo, série/número, ATCUD, datas, cliente, forma/condições de pagamento, viatura/referências quando disponíveis, linhas, totais, estado de parsing e motivos de revisão. Um PDF pode conter várias faturas; cada uma é um documento lógico próprio e guarda páginas de origem.

## Linhas e validações

- Preservar código, descrição, quantidade, unidade, preço, desconto, IVA e total de linha.
- Comparações financeiras usam valor líquido após descontos.
- Erros de normalização, como `105,57` lido como `23 105,57`, são erros de parsing identificáveis, nunca valores inventados.
- "DESEMPENAR" e "SOLDAR" sobre a mesma jante contam uma vez para métricas de jantes; pneus e outros serviços ficam separados de reparações.

### Autoliquidação de IVA

Não recalcular montantes a partir de preço × quantidade. Em documento multipágina:

```text
valor esperado = Σ "A Transportar" das páginas não finais + Σ totais das linhas da página final
```

Comparar o resultado com o `Valor Sujeito` final e nunca contar transportes de página como linhas novas.

### Reclamação

Uma linha `RECLAMAÇÃO` pode ter quantidade, sem preço, desconto, IVA ou total. É válida como linha operacional e vale financeiramente `0,00`.

## Recibos e pagamentos

Recibos não são faturas: apresentam faturas liquidadas e valores processados/pendentes. A relação é N:N: uma fatura pode ser paga por vários recibos e um recibo pode liquidar várias faturas. A associação guarda sempre o valor aplicado. Não inferir automaticamente relações entre faturas de adiantamento/fases; exigir referência explícita ou revisão.

## Revisão

A página de revisão mostra documento, campos/linhas em causa, regra que falhou, valor extraído, valor esperado e motivo legível. Estados mínimos: `RECEIVED`, `CLASSIFIED`, `PARSED`, `VALIDATED`, `REVIEW_REQUIRED`, `FAILED` e `DUPLICATE`.
