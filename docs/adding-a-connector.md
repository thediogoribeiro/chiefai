# Adicionar Um Connector

Cada integração deve viver em `integration/<provider>` e expor um port independente do fornecedor.

## Checklist

1. Criar interface do port com operações de negócio, não nomes de endpoints externos.
2. Criar fake adapter funcional para `local` e `test`.
3. Criar adapter real ativado apenas por configuração explícita.
4. Validar configuração no arranque com mensagens claras.
5. Registar tentativas/resultados via ações ou outbox quando houver efeitos.
6. Nunca expor tokens ao domínio, ao LLM ou aos logs.
7. Adicionar o connector ao `ConnectorRegistry`.
8. Adicionar ferramentas ao `ToolRegistry` apenas quando houver validação Java concreta.
9. Atualizar `policies/agent-tools.yml` com allowlists mínimas.
10. Cobrir com testes que não chamem serviços reais.

## Segurança

O LLM nunca executa integrações diretamente. Ele só propõe ações estruturadas; o backend valida agente, ferramenta, argumentos, idempotência, risco e aprovação.

Ferramentas `EXTERNAL_WRITE` e `DESTRUCTIVE` devem criar `ProposedAction(PENDING_APPROVAL)`, exceto permissões explícitas e estritamente limitadas, como auto-publicação para o spreadsheet pessoal configurado.

## Estrutura sugerida

```text
integration/
└── provider/
    ├── ProviderConnector.java
    ├── FakeProviderConnector.java
    └── RealProviderConnector.java
```

Mantém DTOs externos fora do domínio. Normaliza entradas para `IncomingEvent` ou para um use case de aplicação específico.
