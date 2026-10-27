# Integração entre produtos Rucodel

Cada produto mantém o seu domínio. Integrações devem usar contratos explícitos e eventos idempotentes; nunca acesso direto às tabelas internas de outro produto.

| Informação | Sistema de referência | Consumidor |
| --- | --- | --- |
| Cliente por `número + NIF` | RucoFi | RucoPlan |
| Faturas, recibos e valor aplicado | RucoFi | Cycle Review e relatórios |
| Eventos de processo e secções | legacy → RucoPI | RucoPlan e dashboard |
| KPIs de produção | RucoPI | operação e planeamento |
| Pedido e capacidade diária | RucoPlan | operação |

## Contratos principais

### RucoFi → RucoPlan

RucoPlan pesquisa clientes no RucoFi. A pesquisa pode usar nome, mas a seleção/persistência usa o identificador estável do cliente; RucoPlan não recria regras de identidade.

### Legacy → RucoPI

Eventos com `event_id` e `occurred_at` são idempotentes. Devem guardar origem, versão de contrato, estado de entrega e erro para retry/dead-letter.

### RucoPlan ↔ RucoPI

RucoPlan representa previsão; RucoPI, realidade. Conciliar por referência de serviço/pedido e data operacional, não por totais agregados ou data de recolha.

### Fontes para Cycle Review Luxemburgo

```text
total único = registos Luxemburgo + jantes só registadas em Portugal − duplicações/ajustes
```

As fontes têm de ficar separadas para auditoria. Todas as mensagens devem usar UTC ou timezone explícito; informação financeira e pessoal deve respeitar menor privilégio.
