# Módulo de sistema

`com.chiefai.personalagent.system` separa API, aplicação, domínio e infraestrutura. `SupervisorQueue` é o port de comunicação durável; `PostgresSupervisorQueue` é o adapter consumido pelo supervisor PowerShell independente do Spring Boot. Flyway V4 cria a fila e a proveniência interna dos eventos.

```text
Pedido direto OWNER → Manager/AgentRunner → ProposedAction PENDING_APPROVAL
→ confirmação OWNER → ActionService/ToolExecutor → system_update REQUESTED
→ supervisor externo → staging/build/verify → paragem/troca/arranque/readiness
→ resultado persistido → outbox do ChiefAI
```

O Manager tem `system.get_status`, `system.get_version`, `system.request_update` e `system.get_update_status`. As ferramentas não recebem argumentos. Pelo Telegram, os comandos exatos `/system status`, `/system version`, `/system updates` e `/system update` usam o router e AgentRunner centrais, sem inferência do LLM para autorizar efeitos. Especialistas não podem executar estas ferramentas.

| API autenticada | Operação |
| --- | --- |
| `GET /api/v1/system/status` | processo backend em execução, atualizações ativadas e pedido ativo |
| `GET /api/v1/system/version` | commit do artefacto instalado; `unknown` fora da instalação supervisionada |
| `POST /api/v1/system/update-proposals` | pedido explícito sem corpo ou `{}`, sem query parameters; sessão humana OWNER |
| `GET /api/v1/system/updates/latest` | último pedido enfileirado, ou corpo vazio se ainda não existir |
| `GET /api/v1/system/updates/{id}` | estado e resultado persistidos |

Uma proposta só entra na fila após `POST /api/v1/actions/{actionId}/approve`. A aprovação Telegram ainda não existe. Faz login no dashboard e utiliza a sessão/CSRF da API existente, como descrito no [guia Windows](windows-deployment.md). O mecanismo não simula confirmações. Pedidos por chave interna, metadata forjada, email ou conteúdo transcrito não autorizam a atualização.

A tabela `system_update` guarda UUID do pedido e da ação aprovada, estado, commits anterior/novo, timestamps e erro resumido. Um índice parcial PostgreSQL permite apenas um pedido REQUESTED/RUNNING; o supervisor também usa lock exclusivo no filesystem. Não existe comando `push`.

Estados: `REQUESTED`, `RUNNING`, `NO_CHANGES`, `SUCCEEDED`, `FAILED_PRE_SWITCH`, `FAILED_START`, `ROLLBACK_FAILED`. O supervisor guarda o resultado mesmo com o backend parado. Quando a aplicação está operacional, `notifyCompleted` cria notificações idempotentes no outbox LOCAL e no Telegram OWNER quando configurado. Se o backend não recuperar, consulta o log e a tabela diretamente: a notificação só será enviada quando o backend voltar.

Antes da troca, qualquer falha mantém o JAR anterior. Numa falha de arranque, o supervisor para a candidata e compara o estado Flyway com o anterior: só tenta restaurar o JAR quando esse estado é idêntico e a candidata não alterou ficheiros de migrations. Esta condição conservadora evita assumir que um erro não transacional foi revertido pelo PostgreSQL. Alterações de schema, indisponibilidade da base de dados ou falha de recuperação exigem intervenção manual. Não são executadas down migrations, clean-database ou limpeza de volumes. Uma interrupção do supervisor durante RUNNING é assinalada como recuperação manual no arranque seguinte.
