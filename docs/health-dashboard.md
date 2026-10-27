# Dashboard Saúde

A secção `Saúde` mostra dados persistidos pelo módulo de saúde existente.

## Dados apresentados

Conforme disponível:

- Sleep Score.
- Readiness Score.
- Activity Score.
- HRV média.
- Frequência cardíaca em repouso.
- Duração do sono.
- Tendências por período.

Se não houver dados, a UI mostra estado vazio.

## Endpoints

- `GET /api/v1/health/snapshots`
- `GET /api/v1/health/trends`

## Aviso médico

Toda a análise apresentada é informativa e não constitui diagnóstico médico.
