# Runbook de Revenue Operations (alpha)

## Objetivo
Padronizar a operação comercial-financeira do alpha (faturamento, cobrança, conciliação e escalonamento), reduzindo risco de inadimplência e divergência de contrato.

## Escopo (alpha)
- Planos: `TRIAL`, `STARTER`, `PRO`, `ENTERPRISE`
- Fonte de verdade técnica: estado de assinatura em `dms-document-service` (`/v1/billing/subscription` + webhook)
- Janela operacional: D0 até D+30 por coorte

## Cadência operacional

### Diário (D+0 a D+14)
1. Conferir assinaturas em `PAST_DUE` e `CANCELED`.
2. Confirmar processamento de webhooks sem duplicidade (`eventId` idempotente).
3. Revisar tickets de suporte com tema "cobrança"/"acesso bloqueado".

### Semanal
1. Validar funil de trial -> pago.
2. Revisar downgrades e churn evitável.
3. Atualizar plano de ação do `commercial-risk-register-alpha.md`.

## Fluxos críticos

### 1) Trial expirando (T-3 dias)
- Disparar comunicação preventiva com plano atual + opções de upgrade.
- Confirmar owner responsável e canal preferencial.
- Registrar ação no log de decisão (`commercial-go-live-decision-log-alpha.md`).

### 2) Fatura em atraso (`PAST_DUE`)
- Validar se webhook foi recebido e aplicado.
- Checar tentativa de refresh (`POST /v1/billing/subscription/refresh`) quando houver `externalSubscriptionId`.
- Enviar comunicação de regularização com prazo e impacto operacional.
- Se persistir >72h, escalar para atendimento humano prioritário.

### 3) Downgrade solicitado
- Confirmar impacto de limites por plano (docs/mês, storage, usuários).
- Garantir que cliente recebeu resumo de impacto antes de efetivar mudança.
- Registrar decisão e evidência no scorecard comercial.

## KPIs mínimos (operacionais)
- % de `PAST_DUE` sobre base ativa
- Tempo médio até primeira resposta em casos de cobrança
- Taxa de reversão de inadimplência em até 7 dias
- Taxa de downgrade com retenção ativa (sem churn no D+30)

## Critério de alerta (ATTENTION)
- `PAST_DUE` > 8% da base ativa por 2 dias consecutivos
- Falha de webhook de billing sem reprocessamento em até 1h
- Tickets de cobrança sem primeiro atendimento > SLA definido

## Evidências obrigatórias por rodada
- Export de status de assinatura (snapshot da rodada)
- Lista de casos críticos tratados (anonimizada)
- Atualização do scorecard e do decision log
