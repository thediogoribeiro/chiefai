# RucoPI — produção e KPIs

## Objetivo

RucoPI recebe eventos da produção legacy, mantém histórico e atualiza um dashboard em tempo real por Server-Sent Events (SSE). O foco inicial é duração e produção por secção.

## Evento de entrada

A origem é a outbox legacy `producao_evento_outbox`. O contrato inclui `event_id`, `event_type` (`process_started` ou `process_finished`), `occurred_at`, `servico`, `servico_produto`, `section` e `operador`.

`servico_produto`, `section` e `operador` podem ser nulos. O evento é guardado sem falhar, mas só entra no KPI que seja calculável com segurança. `event_id` é a chave de idempotência. `process_finished` atualiza o dashboard; `process_started` pode enriquecer histórico, mas não emite KPI final.

## Secções

| Código | Secção |
| --- | --- |
| S1 | Decapagem / Ácido / Queimar |
| S2 | Desempeno / Soldadura / Torno / Fresadora |
| S3 | Lixar / Massa |
| S4 | Dar pó / Estufa |
| S5 | Pintura |
| S6 | CNC / Fazer beiras |
| S7 | Verniz |
| S8 | Embalamento |

Pontos de validação: entrada em produção; saída S7/entrada S8; saída para expedição.

## Dashboard e KPIs

Por secção e data: média, mínimo, máximo, jantes concluídas, última atualização e comparação entre média diária e média mensal. Duração vem de `start`/`stop`; registos inválidos não entram na estatística. Subida é vermelha, descida verde. S1, S4 e S7 exigem precisão adequada a durações curtas.

Interface: cabeçalho `RucoPI`, acento `#A91818`, seletor de data e atalho `hoje`. SSE tem heartbeat de 30 segundos.

## Carga

Manter cenários repetíveis: cinco dias com 200 jantes/dia e um dia para teste rápido. Verificar ingestão idempotente, cálculo dos KPIs e SSE.
