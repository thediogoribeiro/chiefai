# Adicionar Um Agente

1. Criar `src/main/resources/agents/{id}/instructions.md`.
2. Criar ficheiros de conhecimento em `src/main/resources/knowledge/{id}/`.
3. Adicionar a definição em `src/main/resources/agents/definitions.yml`.
4. Declarar apenas ferramentas existentes no `ToolRegistry`.
5. Adicionar política em `src/main/resources/policies/agent-tools.yml`.
6. Criar ou reutilizar schema em `src/main/resources/schemas/`.
7. Adicionar testes para routing, allowlist e ações.

O loader falha no arranque se uma ferramenta, schema ou ficheiro referenciado não existir.

As permissões têm de ser declaradas também em `policies/agent-tools.yml` e no mapa de domínios Java de `ToolPolicyService`. O loader rejeita ferramentas que excedam esse mapa. Especialistas não recebem ferramentas para invocar agentes ou `system.*`; ver [permissões](agent-permissions.md). Modelos são configurados em `agents/models.yml`, mantendo os limites de tokens e tool calls.

Todos os novos ficheiros de instruções devem incluir integralmente a secção «Regra obrigatória: credenciais apenas em runtime» de `AGENTS.md`. Não incluir valores reais em Markdown, prompts, conhecimento ou exemplos. Os agentes usam ferramentas autorizadas; apenas os adapters obtêm credenciais em runtime por configuração segura, sem as expor ao modelo.
