# RucoPlan — recolhas e planeamento de fábrica

## Âmbito

RucoPlan regista pedidos, entrega na fábrica e recolha pretendida. Não inclui entrega ao cliente final. Motoristas podem ser independentes e usar números pessoais. Telegram é canal de entrada; as regras de domínio não pertencem ao bot.

## Formulário do motorista

1. Cliente.
2. Jantes bipartidas.
3. Jantes lavadas.
4. Restantes jantes normais.
5. Data de entrega na fábrica.
6. Intervalo de entrega: `1` 9h–14h, `2` 14h–19h, `3` 19h–madrugada.
7. Dia em que devem estar prontas.
8. Intervalo de recolha: `1` 9h–14h, `2` 14h–19h, `3` 19h–madrugada.
9. Nota extra.
10. Confirmação.

A quantidade total é a soma dos três tipos. Estados úteis: `DRAFT`, `PENDING_CONFIRMATION`, `CONFIRMED`, `PLANNED`, `IN_FACTORY`, `READY_FOR_PICKUP`, `PICKED_UP`, `CANCELLED` e `CHANGED`.

## Capacidade e planeamento

O frontend administrativo apresenta amanhã, dias seguintes e passados, target máximo e mínimo, carga planeada, feito e carry-over. O target é agregado: não é separado por tipos de jante.

Distribuir trabalho equitativamente, respeitando chegada, prazo de recolha, capacidade máxima, carga existente e carry-over. Prazos de bipartidas, lavadas e normais estão **em aberto** e devem ser configuráveis.

No fecho do turno, trabalho não concluído passa a carry-over e é priorizado no replaneamento, mantendo data planeada original e rastreabilidade.

## Integrações

Reutilizar identidade de cliente do RucoFi. RucoPI fornece produção concluída para conciliar plano e realidade; nunca inferir conclusão pela data de recolha.
