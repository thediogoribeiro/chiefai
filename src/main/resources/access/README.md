# ChiefAI Access Configuration

Este diretório contém apenas um exemplo de configuração. Não coloques aqui um ficheiro real com credenciais.

Usa um ficheiro externo e aponta para ele com:

```bash
CHIEFAI_ACCESS_FILE=/caminho/seguro/owner-access.yml
```

O ficheiro real deve seguir a estrutura de `owner-access.example.yml` e conter:

- `owner.username`
- `owner.passwordHash` com BCrypt
- `owner.telegram.allowedUserId`
- `owner.telegram.allowedChatId`

O número de telefone é apenas uma nota de verificação. Bots Telegram não recebem automaticamente o número de telefone do utilizador.
