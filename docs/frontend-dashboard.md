# Frontend Dashboard

O frontend do ChiefAI é servido pelo próprio Spring Boot em `src/main/resources/static`. Não há segundo projeto nem pipeline Node.

## Rotas

- `/`: visão geral.
- `/finance`: dashboard financeiro.
- `/health`: dashboard de saúde.

As rotas são mantidas por `history.pushState` e servidas por `FrontendController`.

## Módulos

A sidebar é gerada por configuração de módulos:

```javascript
{ id: "finance", label: "Finanças", route: "/finance", icon: "wallet", enabled: true }
```

O backend também expõe `GET /api/v1/dashboard/modules`, já filtrando módulos desativados. Para adicionar um módulo novo:

1. Adicionar entrada no endpoint de módulos.
2. Adicionar rota no `FrontendController`.
3. Adicionar função de renderização em `app.js`.
4. Evitar colocar lógica financeira no shell.

## Atualização

O frontend usa polling controlado na secção financeira. Quando houver SSE/WebSocket no projeto, pode substituir o polling sem alterar os endpoints financeiros.

## Estados

A UI inclui estados de loading, vazio e erro. Dados inexistentes são mostrados como estados vazios, nunca como valores inventados.
