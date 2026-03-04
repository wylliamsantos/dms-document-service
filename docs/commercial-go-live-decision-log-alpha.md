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

## 2026-02-28 — Rodada 4 (checkpoint pós-merge da PR final)

- Decisão: ATTENTION
- Escopo avaliado: prontidão final com governança técnica encerrada e preparação do ritual final de lançamento
- Responsáveis pela decisão:
  - Produto: pendente nomeação
  - Engenharia: pendente nomeação
  - Comercial: pendente nomeação
  - Suporte: pendente nomeação

### Evidências consultadas
- `dms-go-live-alpha-roadmap.md` (itens 1–10 concluídos)
- PR crítica final mergeada: `dms-document-service#24`
- `docs/commercial-go-no-go-session-2026-02-28.md`
- `docs/commercial-launch-communications-alpha.md`

### Justificativa
A trilha técnica está concluída e sem PRs abertas nos repositórios críticos. O único bloqueio remanescente é de governança: falta a sessão síncrona final para decisão formal (`GO/ATTENTION/NO-GO`) com owners nomeados e confirmação da janela de comunicação.

### Ações obrigatórias
- [ ] Conduzir sessão GO/NO-GO final com stakeholders e registrar ata.
- [ ] Registrar decisão final com owners por área e plano de comunicação no roadmap e neste log.

## 2026-03-03 — Rodada 5 (checkpoint pós-merge da PR #25)

- Decisão: ATTENTION
- Escopo avaliado: prontidão final com governança técnica encerrada e aguardando ritual executivo final
- Responsáveis pela decisão:
  - Produto: pendente nomeação
  - Engenharia: pendente nomeação
  - Comercial: pendente nomeação
  - Suporte: pendente nomeação

### Evidências consultadas
- `dms-go-live-alpha-roadmap.md` (itens 1–10 concluídos)
- PR crítica final de governança mergeada: `dms-document-service#25`
- `docs/commercial-go-no-go-session-2026-03-03.md`
- `docs/commercial-launch-communications-alpha.md`

### Justificativa
Com o merge da PR `#25`, a trilha técnica e documental permanece concluída e sem pendências de implementação/CI nos repositórios críticos. O bloqueio remanescente é exclusivamente de governança: realizar a sessão final com stakeholders para formalizar o veredito (`GO/ATTENTION/NO-GO`), nomear owners e confirmar a execução da comunicação.

### Ações obrigatórias
- [ ] Realizar a sessão GO/NO-GO final com stakeholders e registrar ata com participantes.
- [ ] Publicar decisão final no roadmap e neste log com owners nomeados e janela de comunicação confirmada.

## 2026-03-03 — Rodada 6 (checkpoint pós-merge das correções de upload)

- Decisão: ATTENTION
- Escopo avaliado: prontidão final após estabilizações de upload e fechamento de checkpoints documentais
- Responsáveis pela decisão:
  - Produto: pendente nomeação
  - Engenharia: pendente nomeação
  - Comercial: pendente nomeação
  - Suporte: pendente nomeação

### Evidências consultadas
- `dms-go-live-alpha-roadmap.md` (itens 1–10 concluídos)
- PRs mergeadas no fechamento técnico/documental: `dms-document-service#26`, `#27`, `#28`
- `docs/commercial-go-no-go-session-2026-03-03.md`
- `docs/commercial-launch-communications-alpha.md`

### Justificativa
As correções recentes de governança e upload foram mergeadas com CI verde e não há PRs abertas nos repositórios críticos. O bloqueio remanescente continua exclusivamente de governança executiva: realizar a sessão final síncrona GO/NO-GO, nomear owners por área e confirmar a janela final de comunicação.

### Ações obrigatórias
- [ ] Realizar a sessão GO/NO-GO final com stakeholders e registrar ata com participantes.
- [ ] Publicar decisão final no roadmap e neste log com owners nomeados e janela de comunicação confirmada.

## 2026-03-04 — Rodada 7 (checkpoint de prontidão sem pendência técnica)

- Decisão: ATTENTION
- Escopo avaliado: estabilidade final pós-merge da governança rodada 6 e confirmação de ausência de backlog técnico aberto
- Responsáveis pela decisão:
  - Produto: pendente nomeação
  - Engenharia: pendente nomeação
  - Comercial: pendente nomeação
  - Suporte: pendente nomeação

### Evidências consultadas
- `dms-go-live-alpha-roadmap.md` (itens 1–10 concluídos)
- PR de governança mais recente mergeada: `dms-document-service#29`
- `docs/commercial-go-no-go-session-2026-03-03.md`
- `docs/commercial-launch-communications-alpha.md`

### Justificativa
Não há pendências técnicas ou de CI nos repositórios críticos; o bloqueio restante continua exclusivamente executivo, dependente da sessão síncrona final com stakeholders para formalizar o veredito (`GO/ATTENTION/NO-GO`) e owners por área.

### Ações obrigatórias
- [ ] Realizar sessão executiva final GO/NO-GO com stakeholders e registrar decisão formal.
- [ ] Confirmar owners por área e publicar janela/canais finais de comunicação.
