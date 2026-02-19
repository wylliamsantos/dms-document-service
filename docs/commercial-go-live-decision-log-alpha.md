# Decision Log — Go-Live Comercial (Alpha)

Objetivo: registrar decisões formais de **GO / ATTENTION / NO-GO** por rodada de revisão comercial, com evidências e responsáveis.

## Como usar

- Abrir uma nova entrada a cada checkpoint relevante (semanal ou extraordinário).
- Referenciar PRs, dashboards, incidentes e artefatos usados na decisão.
- Em caso de `ATTENTION` ou `NO-GO`, registrar plano de ação com owner e prazo.

## Template de entrada

```md
## YYYY-MM-DD — Rodada <N>

- Decisão: GO | ATTENTION | NO-GO
- Escopo avaliado: (ex.: alpha tenant A/B, onboarding self-service, billing starter)
- Responsáveis pela decisão:
  - Produto:
  - Engenharia:
  - Comercial:
  - Suporte:

### Evidências consultadas
- KPI aquisição/conversão: <link ou referência>
- Estabilidade técnica (5xx/latência/incidentes): <link ou referência>
- Cobrança/inadimplência: <link ou referência>
- SLA de suporte: <link ou referência>
- Riscos abertos: <link ou referência>

### Justificativa
<resumo objetivo da decisão>

### Ações obrigatórias
- [ ] Ação 1 — owner — prazo
- [ ] Ação 2 — owner — prazo
```

## Histórico

## 2026-02-19 — Rodada 1

- Decisão: ATTENTION
- Escopo avaliado: pacote documental mínimo de go-to-market (alpha)
- Responsáveis pela decisão:
  - Produto: pendente nomeação
  - Engenharia: pendente nomeação
  - Comercial: pendente nomeação
  - Suporte: pendente nomeação

### Evidências consultadas
- `docs/commercial-go-live-checklist-alpha.md`
- `docs/commercial-launch-metrics-alpha.md`
- `docs/commercial-risk-register-alpha.md`

### Justificativa
Pacote documental mínimo está consolidado, porém ainda existem pendências técnicas com impacto comercial direto (deduplicação fim-a-fim no consumidor oficial e governança final das PRs abertas relacionadas ao hardening operacional).

### Ações obrigatórias
- [ ] Fechar evidência downstream de deduplicação fim-a-fim por `idempotencyKey` (item 7).
- [ ] Consolidar governança final das PRs comerciais/hardening ainda abertas.
