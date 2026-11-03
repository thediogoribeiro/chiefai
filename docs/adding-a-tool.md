# Adicionar Uma Ferramenta

1. Registar a ferramenta em `ToolRegistry` com nome único, descrição, schema, risco e handler.
2. Escolher `READ_ONLY`, `INTERNAL_WRITE`, `EXTERNAL_WRITE` ou `DESTRUCTIVE`.
3. Garantir validação de argumentos no handler.
4. Usar idempotency key para efeitos.
5. Adicionar a ferramenta apenas às allowlists dos agentes autorizados.
6. Para integrações reais, criar um port e um adapter em `integration/{provider}`.
7. Cobrir autorização, idempotência e erro funcional em testes.

O LLM nunca recebe tokens nem executa integrações diretamente.

As allowlists efetivas intersectam o mapa Java de `ToolPolicyService`, `policies/agent-tools.yml` e a definição do agente. Define um schema de argumentos fechado em `ToolRegistry.argumentSchema`; o gateway usa-o para saídas estruturadas e o executor valida-o novamente. Não acrescentes ferramentas genéricas de terminal, filesystem, Git ou invocação de agentes.
