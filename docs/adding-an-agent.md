# Adicionar Um Agente

1. Criar `src/main/resources/agents/{id}/instructions.md`.
2. Criar ficheiros de conhecimento em `src/main/resources/knowledge/{id}/`.
3. Adicionar a definição em `src/main/resources/agents/definitions.yml`.
4. Declarar apenas ferramentas existentes no `ToolRegistry`.
5. Adicionar política em `src/main/resources/policies/agent-tools.yml`.
6. Criar ou reutilizar schema em `src/main/resources/schemas/`.
7. Adicionar testes para routing, allowlist e ações.

O loader falha no arranque se uma ferramenta, schema ou ficheiro referenciado não existir.
