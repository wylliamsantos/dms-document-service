# Billing user-seat checkpoint contract

Este documento define o contrato para o serviço de identidade/provisionamento consumir o checkpoint de limite de usuários antes de criar novos usuários no tenant.

## Endpoint

- **Método/rota:** `POST /v1/billing/limits/users/check`
- **Auth:** Bearer JWT com role `ROLE_OWNER` ou `ROLE_ADMIN`
- **Headers obrigatórios:**
  - `Authorization: Bearer <token>`
  - `TransactionId: <uuid-ou-correlation-id>`
  - `X-Tenant-Id: <tenant_id>`

## Comportamento esperado

- **204 No Content**: provisioning permitido para o plano atual.
- **417 Expectation Failed**: provisioning bloqueado por limite de assentos (ou assinatura inativa).
  - corpo JSON contém mensagem orientando upgrade/regularização.
- **401/403**: token inválido ou sem permissão.

## Momento correto de uso

No fluxo de criação de usuário do serviço de identidade:

1. Resolver tenant do solicitante/contexto.
2. Chamar `POST /v1/billing/limits/users/check`.
3. Só criar o usuário se resposta for **204**.
4. Se resposta for **417**, retornar erro de negócio amigável no canal chamador.

## Exemplo de chamada

```bash
curl -i -X POST "http://localhost:18080/v1/billing/limits/users/check" \
  -H "Authorization: Bearer $TOKEN" \
  -H "TransactionId: user-provision-$(date +%s)" \
  -H "X-Tenant-Id: tenant-dev"
```

## Exemplo de resposta bloqueada

```json
{
  "message": "Limite de usuários ativos do plano atingido (3/3). Faça upgrade para adicionar novos usuários.",
  "type": "VALID"
}
```

## Observações operacionais

- O endpoint reutiliza a política por plano já aplicada no upload (`TRIAL=3`, `STARTER=10`, `PRO=50`, `ENTERPRISE=ilimitado`).
- A contagem de usuários ativos depende do diretório externo quando habilitado:
  - `DMS_BILLING_USER_DIRECTORY_ENABLED=true`
  - `DMS_BILLING_USER_DIRECTORY_BASE_URL=<url-do-diretorio>`
- Sem diretório ativo, o comportamento é fail-open (não bloqueia) para não quebrar ambientes legados.
