# Rucodel — documentação de produto

Requisitos confirmados para os produtos Rucodel. São referência para evolução funcional e não substituem documentos fiscais de origem ou validação contabilística.

| Produto | Objetivo | Documento |
| --- | --- | --- |
| RucoFi | Documentos financeiros, faturação e pagamentos | [RucoFi](rucofi.md) |
| RucoPI | Eventos de produção e KPIs em tempo real | [RucoPI](rucopi.md) |
| RucoPlan | Pedidos de recolha e capacidade da fábrica | [RucoPlan](rucoplan.md) |

## Convenções

- Idempotência em importações e eventos.
- Valores financeiros decimais, nunca `float`.
- Preservar evidência de parsing e avisos; não corrigir dados silenciosamente.
- Regras marcadas **em aberto** não devem ser assumidas nem hardcoded.
- Integrar por contratos explícitos, não por leitura direta da base de dados de outro produto.

## Índice

- [RucoFi](rucofi.md)
- [RucoPI](rucopi.md)
- [RucoPlan](rucoplan.md)
- [Integrações](integrations.md)
- [Cycle Review Luxemburgo](cycle-review.md)
- [Decisões e questões em aberto](decisions-and-open-questions.md)
