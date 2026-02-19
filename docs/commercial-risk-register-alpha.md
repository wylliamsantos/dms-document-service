# Registro de riscos comerciais (alpha)

Este artefato documenta riscos de go-live comercial no recorte alpha, com gatilhos, impacto e plano de mitigação.

## Escala
- **Probabilidade:** Baixa / Média / Alta
- **Impacto:** Baixo / Médio / Alto

## Riscos ativos

### R1 — Falha de deduplicação no processamento assíncrono
- **Probabilidade:** Média
- **Impacto:** Alto
- **Gatilho:** Reprocessamento do mesmo arquivo gerar duplicidade visível ao cliente.
- **Mitigação em andamento:**
  - `idempotencyKey` determinístico já publicado no `watch-service`.
  - Pendência: validação no consumidor oficial com evidência fim-a-fim sem duplicidade.
- **Owner:** Engenharia (Backend)
- **Status:** Aberto

### R2 — Gargalo de aprovação externa em PRs de go-live
- **Probabilidade:** Alta
- **Impacto:** Médio
- **Gatilho:** PRs críticas bloqueadas por `REVIEW_REQUIRED` próximo da janela de lançamento.
- **Mitigação em andamento:**
  - Planejamento incremental com PRs pequenas.
  - Checklist de governança com revisão diária de bloqueios.
- **Owner:** Engenharia + Tech Lead
- **Status:** Aberto

### R3 — Suporte inicial acima da capacidade no D0-D+7
- **Probabilidade:** Média
- **Impacto:** Médio
- **Gatilho:** Volume de chamados acima do previsto no playbook alpha.
- **Mitigação em andamento:**
  - Templates de suporte padronizados.
  - SLA por severidade definido no playbook.
  - Escalonamento rápido para incidentes P1.
- **Owner:** Suporte + Produto
- **Status:** Monitorando

### R4 — Desalinhamento comercial vs limites técnicos por plano
- **Probabilidade:** Baixa
- **Impacto:** Alto
- **Gatilho:** Oferta comercial prometer capacidade acima do enforcement técnico.
- **Mitigação em andamento:**
  - Pricing alpha alinhado ao enforcement técnico de `docs/mês`, `storage` e `usuários`.
  - FAQ de vendas com guardrails contra overpromise.
- **Owner:** Produto + Comercial
- **Status:** Monitorando

## Critério de saída para go-live alpha
- Nenhum risco **Alto/Alto** sem mitigação ativa.
- R1 com evidência fim-a-fim anexada no stack oficial.
- Revisão do registro de riscos no gate semanal `GO/ATTENTION/NO-GO`.
