# Instruções Para Agentes De Desenvolvimento

Este repositório implementa o Personal Agent Core como monólito modular Spring Boot.

## Estrutura

- `api`: controllers e DTOs HTTP.
- `application`: casos de uso, transações e coordenação.
- `domain`: enums, invariantes e conceitos sem Spring/JPA.
- `infrastructure`: JPA, clients HTTP e adapters externos.

Controllers não chamam repositories diretamente e entidades JPA não são devolvidas pela API.

## Integrações

Adiciona integrações reais atrás de ports. Mantém fakes para local/test. Não implementar OAuth ou chamadas externas dentro do domínio.

Connectors vivem em `integration/<provider>` e devem ser registados no `ConnectorRegistry`. Gmail, Google Sheets e Calendar partilham OAuth Google do utilizador; não usar service account para Gmail pessoal.

O fluxo de despesas lê apenas Gmail com a etiqueta configurada, nunca varre toda a mailbox. Despesas ficam em PostgreSQL e aparecem no dashboard ChiefAI; não reintroduzir sincronização automática para Sheets/Excel.

## Segurança

Não coloques credenciais, tokens, prompts completos ou conhecimento pessoal/empresarial sensível no código. O LLM só pode ver ferramentas autorizadas e toda execução tem validação Java.

## Comandos

```bash
./scripts/start-backend.sh
./scripts/run-tests.sh
./mvnw verify
```

## Regras

- Preservar alterações existentes do utilizador.
- Flyway cria schema; Hibernate apenas valida.
- Não usar H2 nos testes de persistência.
- Ações externas exigem aprovação humana.
- Ferramentas `google_sheets.*`, Gmail, Calendar, Trello, Teams, WhatsApp e outros efeitos externos nunca recebem credenciais via LLM.
- Montantes financeiros usam `BigDecimal`, nunca `double` ou `float`.
- Exportação Excel financeira é sempre manual via API/frontend e deve proteger contra formula injection.
- O conhecimento em `resources/knowledge` deve ser preenchido apenas com informação explicitamente fornecida.
