# Controlo De Acesso

ChiefAI usa um único utilizador proprietário com papel `OWNER`.

Não existe registo público, recuperação de password, múltiplos papéis ou gestão de utilizadores no MVP.

## Modelo

- Frontend e APIs privadas usam sessão Spring Security.
- Login: `POST /api/v1/auth/login`.
- Logout: `POST /api/v1/auth/logout`.
- Sessão atual: `GET /api/v1/auth/me`.
- Webhooks Telegram e WhatsApp não usam sessão web; usam os seus próprios secrets.
- Integrações técnicas podem continuar a usar `X-Internal-Api-Key` quando configurado.

Em produção, a configuração OWNER é obrigatória.

## Ficheiro De Acesso

Cria um ficheiro local fora do Git, por exemplo:

```yaml
owner:
  username: chiefai
  passwordHash: "$2a$10$..."
  telegram:
    allowedUserId: "123456789"
    allowedChatId: "123456789"
    declaredPhoneNumber: "+351..."
```

Depois aponta a aplicação para esse ficheiro:

```env
CHIEFAI_ACCESS_FILE=/caminho/local/owner-access.yml
```

Existe apenas um exemplo seguro em:

```text
src/main/resources/access/owner-access.example.yml
```

O ficheiro real `owner-access.yml` está no `.gitignore`.

## Gerar Hash BCrypt

Nunca guardes a password em texto simples. Uma forma de gerar o hash usando as dependências do projeto:

```bash
./mvnw -q -DskipTests dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
jshell --class-path "target/classes:$(cat target/classpath.txt)" <<'EOF'
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
System.out.println(new BCryptPasswordEncoder().encode("troca-esta-password"));
EOF
```

Copia apenas o hash BCrypt para o ficheiro local ou variável `CHIEFAI_OWNER_PASSWORD_HASH`.

## Telegram

Um bot Telegram não recebe automaticamente o número de telefone do utilizador. Por isso, a autorização principal é feita por:

- `from.id`;
- `chat.id`.

O número de telefone em `declaredPhoneNumber` é apenas informação declarativa/verificação manual. Só pode ser comparado se o utilizador partilhar explicitamente um contacto com o bot.

Mensagens de utilizadores ou chats não autorizados não criam eventos e recebem uma resposta neutra.

## Variáveis Úteis

```env
CHIEFAI_OWNER_USERNAME=
CHIEFAI_OWNER_PASSWORD_HASH=
CHIEFAI_TELEGRAM_OWNER_USER_ID=
CHIEFAI_TELEGRAM_OWNER_CHAT_ID=
CHIEFAI_OWNER_PHONE_NUMBER=

TELEGRAM_ENABLED=false
TELEGRAM_WEBHOOK_SECRET=
TELEGRAM_BOT_TOKEN=
```

Em produção, configura também uma API key interna se usares integrações técnicas:

```env
INTERNAL_API_KEY_ENABLED=true
INTERNAL_API_KEY=
```

## Revogar Ou Alterar Acesso

1. Altera o hash BCrypt ou os IDs Telegram no ficheiro externo.
2. Reinicia a aplicação.
3. Invalida sessões antigas com logout ou reinício da aplicação.
4. Troca `TELEGRAM_WEBHOOK_SECRET` se suspeitares de exposição.

Não coloques passwords, hashes reais, tokens, números de telefone ou IDs privados em Markdown, código ou logs.
