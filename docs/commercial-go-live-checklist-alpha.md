# Checklist de Go-Live Comercial (Alpha)

## Objetivo
Garantir uma validação mínima e repetível antes de liberar o alpha para novos tenants pagantes.

## Critérios de entrada
- Pricing publicado e alinhado com limites técnicos por plano.
- Termos comerciais e política de privacidade revisados.
- Playbook de suporte validado com time de operação.
- Fluxo de billing funcional (`trial`, assinatura, webhook idempotente assinado).

## Gate de prontidão (Go/No-Go)

### 1) Oferta e contrato
- [ ] Tabela de planos validada com Produto/Comercial (`TRIAL`, `STARTER`, `PRO`, `ENTERPRISE`).
- [ ] Termos comerciais revisados e aprovados.
- [ ] Política de privacidade revisada e publicada.

### 2) Operação e suporte
- [ ] Canais de suporte definidos (e-mail/ticket).
- [ ] SLA inicial configurado por severidade (P1..P4).
- [ ] Modelo de resposta inicial validado (ack + ETA + próximo update).

### 3) Financeiro e cobrança
- [ ] Webhook de billing com validação de assinatura ativo em produção.
- [ ] Segredo de webhook (`DMS_BILLING_WEBHOOK_SECRET`) rotacionado e armazenado em cofre.
- [ ] Plano e status de assinatura sincronizando corretamente via refresh.

### 4) Segurança e conformidade operacional
- [ ] Rate limit ativo e validado em runtime.
- [ ] CORS de produção sem curingas / localhost.
- [ ] Checklist de segurança operacional executado (`docs/security-operational-checklist.md`).

### 5) Recuperação e continuidade
- [ ] Drill de backup/restore executado com sucesso nas últimas 24h.
- [ ] RPO/RTO dentro dos alvos definidos.

## Evidências mínimas por rodada
- Link de PR/commit com artefatos comerciais atualizados.
- Link de logs/smokes de billing e segurança.
- Registro de decisão Go/No-Go com data/hora e responsável.

## Decisão final
- Status: [ ] GO  [ ] NO-GO
- Data/hora (UTC): ____________________
- Responsável: ____________________
- Observações: ____________________
