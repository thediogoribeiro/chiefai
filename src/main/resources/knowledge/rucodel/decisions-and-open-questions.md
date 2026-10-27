# Decisões e questões em aberto

## Confirmado

| Área | Decisão |
| --- | --- |
| RucoFi | Classificar documento antes de selecionar parser. |
| RucoFi | Recibo–fatura é N:N e guarda valor aplicado. |
| RucoFi | Cliente é único por número de cliente + NIF. |
| RucoFi | Reclamação sem valores monetários vale financeiramente 0,00. |
| RucoPI | `process_finished` atualiza SSE; heartbeat de 30 segundos. |
| RucoPI | Produto, secção e operador podem ser nulos. |
| RucoPlan | Tipos: bipartidas, lavadas, normais, nessa ordem. |
| RucoPlan | Target é agregado; entrega final ao cliente está fora do âmbito. |

## Em aberto

| Área | Questão | Tratamento atual |
| --- | --- | --- |
| RucoPlan | Prazo por tipo de jante | Configurável; não hardcode. |
| RucoPlan | Conflito entre target mínimo e prazo | Registar conflito e permitir decisão admin. |
| RucoFi | Ligação de faturas de adiantamento/fases | Exigir referência explícita ou revisão. |
| RucoFi | Matching de recibo sem referência | Não aplicar pagamento automaticamente. |
| RucoPI | KPI com evento sem produto/secção | Guardar evento; excluir da métrica não demonstrável. |
| Integrações | Contrato RucoPlan–RucoPI | Definir API/evento versionado antes de implementar. |

Quando uma questão é fechada, atualizar este ficheiro e o documento do produto afetado na mesma alteração.
