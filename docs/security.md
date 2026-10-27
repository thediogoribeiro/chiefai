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
