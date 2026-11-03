# OpenAI e modelos

ChiefAI usa a OpenAI API através de `LanguageModelGateway` e `OpenAiLanguageModelGateway`, com Responses API e resultados estruturados. Não usa login, cookies, password ou automação da interface ChatGPT. A subscrição ChatGPT e a faturação da API são separadas; a API não herda chats, memória ou projetos ChatGPT.

Cria uma chave no projeto da plataforma OpenAI com o acesso necessário e define `OPENAI_API_KEY` no ambiente da conta que executa o backend ou injeta-a através do teu secret store. Não a coloques em YAML versionado, scripts, mensagens ao modelo ou logs.

```dotenv
OPENAI_ENABLED=true
OPENAI_API_KEY=
OPENAI_MANAGER_MODEL=gpt-6-luna
OPENAI_SPECIALIST_MODEL=gpt-6-luna
OPENAI_RUCODEL_MODEL=
OPENAI_HEALTH_MODEL=
OPENAI_PERSONAL_MODEL=
OPENAI_MANAGER_MAX_OUTPUT_TOKENS=2048
OPENAI_SPECIALIST_MAX_OUTPUT_TOKENS=2048
OPENAI_TIMEOUT=30s
```

`src/main/resources/agents/models.yml` é importado pela configuração Spring; suporta as variáveis acima. Um override vazio de especialista usa o modelo default. Para compatibilidade, `OPENAI_ROUTER_MODEL` e `OPENAI_MODEL` continuam como fallback quando os novos valores não estão preenchidos. Sem qualquer modelo configurado, o default é `gpt-6-luna`. A escolha não muda automaticamente com preços. O Manager e cada especialista podem ter modelos diferentes.

Na consulta oficial de 2026-10-27, [GPT-6 Luna](https://developers.openai.com/api/docs/models/gpt-6-luna) existe na API e suporta structured outputs e function calling. Não foi usado acesso à conta para confirmar elegibilidade, quotas ou faturação. Confirma estes pontos no teu projeto antes de ativar. Consulta os [modelos](https://developers.openai.com/api/docs/models) e [preços oficiais](https://developers.openai.com/api/docs/pricing); os preços podem mudar. O default económico não constitui validação clínica ou financeira: cálculos e invariantes continuam em Java/SQL.

O gateway usa propostas de ferramentas no schema estruturado já existente, sujeitas a política e aprovação Java. Não disponibiliza ferramentas nativas de shell, filesystem, execução de código ou pesquisa de ficheiros da OpenAI. [Structured outputs](https://developers.openai.com/api/docs/guides/structured-outputs) não substitui a validação Java.

Os limites de saída aceites são 128–16384 tokens. O timeout HTTP deve ser positivo e no máximo cinco minutos. As definições de agentes limitam tool calls (Manager: 1; especialistas: 3), validadas também no runner. Há no máximo três tentativas de erros transitórios 500/503, cada uma limitada pelo timeout. Os logs incluem modelo, duração e contagens de tokens, sem chave, prompts completos, resposta médica/financeira ou chain of thought. O corpo usa `store:false`.

Para funcionar localmente sem chamadas externas:

```dotenv
OPENAI_ENABLED=false
```

O gateway fake permanece determinístico e os testes usam-no. Não é necessário ter chave para executar os testes. A extração financeira real OpenAI continua limitada pelo schema de despesas já existente no MVP; a sua ativação não foi inventada por esta alteração.
