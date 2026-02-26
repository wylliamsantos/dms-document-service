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
- [x] Fechar evidência downstream de deduplicação fim-a-fim por `idempotencyKey` (item 7).
- [x] Consolidar governança final das PRs comerciais/hardening ainda abertas.

## 2026-02-25 — Rodada 2 (pré-sessão stakeholders)

- Decisão: ATTENTION
- Escopo avaliado: prontidão final pré-ritual GO/NO-GO com stakeholders (engenharia/comercial/operação)
- Responsáveis pela decisão:
  - Produto: pendente nomeação
  - Engenharia: pendente nomeação
  - Comercial: pendente nomeação
  - Suporte: pendente nomeação

### Evidências consultadas
- `dms-go-live-alpha-roadmap.md` (itens 1–10 concluídos)
- PR de governança final mergeada: `dms-document-service#22`
- `docs/commercial-go-no-go-session-2026-02-20.md`
- `docs/commercial-go-live-checklist-alpha.md`

### Justificativa
A trilha técnica e de governança de PR está concluída, sem pendências abertas nos repositórios críticos. O único gap remanescente para decisão formal de lançamento é executar a sessão síncrona GO/NO-GO com stakeholders e registrar o veredito final (GO/ATTENTION/NO-GO) com owners nomeados.

### Ações obrigatórias
- [ ] Conduzir sessão GO/NO-GO com stakeholders e preencher responsáveis por área.
- [ ] Registrar decisão final e plano de comunicação no log/roadmap.

## 2026-02-26 — Rodada 3 (checkpoint pós-governança técnica)

- Decisão: ATTENTION
- Escopo avaliado: prontidão final após merge integral das pendências de PR na trilha Go-Live Alpha
- Responsáveis pela decisão:
  - Produto: pendente nomeação
  - Engenharia: pendente nomeação
  - Comercial: pendente nomeação
  - Suporte: pendente nomeação

### Evidências consultadas
- `dms-go-live-alpha-roadmap.md` (itens 1–10 concluídos)
- PR de governança final mergeadas: `dms-document-service#23`, `dms-frontend#12`
- `docs/commercial-go-no-go-session-2026-02-20.md`
- `docs/commercial-go-live-checklist-alpha.md`

### Justificativa
Todos os repositórios críticos estão sem PRs abertas e sem pendências técnicas de implementação/CI para o escopo Alpha. A única pendência remanescente é ritualística: executar a sessão formal GO/NO-GO com stakeholders, nomear owners por área e registrar decisão final com plano de comunicação.

### Ações obrigatórias
- [ ] Agendar e conduzir sessão GO/NO-GO final com stakeholders (Produto, Engenharia, Comercial, Suporte).
- [ ] Registrar decisão final (`GO/ATTENTION/NO-GO`) com riscos residuais, owners e janela de comunicação no roadmap e neste log.
