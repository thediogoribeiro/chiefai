# Segurança

- Nunca guardar credenciais reais no repositório.
- `OPENAI_API_KEY`, tokens e segredos vêm de variáveis de ambiente.
- O perfil `production` exige `X-Internal-Api-Key`.
- O webhook Telegram usa `X-Telegram-Bot-Api-Secret-Token` quando ativo.
- Prompts completos, áudio, emails integrais e tokens não devem ser registados em logs.
- Conteúdo de Telegram, email, Teams e documentos é tratado como não confiável.
- O LLM só recebe ferramentas permitidas para o agente.
- Antes de executar, Java valida novamente a allowlist e a existência da ferramenta.
- `EXTERNAL_WRITE` e `DESTRUCTIVE` exigem aprovação.

## Credenciais e instruções de agentes

É obrigatório manter todos os ficheiros Markdown, incluindo instruções, documentação e conhecimento, sem credenciais reais. Exemplos usam apenas placeholders ou nomes de variáveis. Nunca copiar valores de `.env` para estes ficheiros. Esta regra aplica-se ao Manager, a todos os especialistas e a futuros agentes; está declarada em `AGENTS.md` e em cada `agents/*/instructions.md`.

Os agentes usam ferramentas autorizadas sem receber segredos. Os adapters obtêm credenciais em runtime através de variáveis de ambiente, secret stores ou fornecedores seguros configurados pelo administrador, utilizando-as apenas pelo tempo necessário. Não colocar segredos em prompts, contexto, memória do agente, argumentos/resultados de ferramentas, respostas ou logs. Configuração em falta deve ser comunicada pelo nome da variável/integração, sem pedir o valor no chat.

Estas instruções não garantem segurança por si só: continuam a ser necessárias as políticas Java, a proteção das fontes de segredos e as permissões mínimas da conta de execução.
