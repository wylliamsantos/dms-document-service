# Commercial Go-Live Scorecard (Alpha)

## Objetivo
Fornecer uma leitura rápida e auditável de prontidão comercial antes de cada decisão de lançamento (`GO`, `ATTENTION`, `NO-GO`).

> Use junto com:
> - `docs/commercial-go-live-decision-log-alpha.md`
> - `docs/commercial-approval-matrix-alpha.md`
> - `docs/commercial-launch-metrics-alpha.md`
> - `docs/commercial-risk-register-alpha.md`

## Regra de avaliação
Cada critério recebe uma nota:
- **2 = verde (pronto)**
- **1 = amarelo (atenção, com plano ativo)**
- **0 = vermelho (bloqueador)**

### Gate final
- **GO**: nenhum critério em vermelho e score total >= 14.
- **ATTENTION**: sem bloqueador crítico, mas score entre 10 e 13.
- **NO-GO**: qualquer bloqueador crítico ou score < 10.

## Scorecard (preencher por rodada)

| Critério | Peso | Nota (0-2) | Evidência | Observações |
|---|---:|---:|---|---|
| Pricing e termos publicados e consistentes | 1 |  | links de docs |  |
| Política de privacidade operacional validada | 1 |  | link de doc + revisão |  |
| Playbook/templating de suporte revisados | 1 |  | links de docs |  |
| Matriz de aprovação com sign-off das áreas | 2 |  | `commercial-approval-matrix-alpha.md` |  |
| KPIs D0-D+14 definidos e medíveis | 2 |  | `commercial-launch-metrics-alpha.md` |  |
| Riscos críticos com mitigação ativa | 2 |  | `commercial-risk-register-alpha.md` |  |
| Comunicação de lançamento preparada (GO/ATTENTION/NO-GO) | 1 |  | `commercial-launch-communications-alpha.md` |  |
| Evidência técnica sem bloqueadores P1/P0 (inclui item 7) | 2 |  | PRs/logs/smokes |  |

**Score total (máx. 24):** `____`

## Checklist rápido de consistência
- [ ] Decisão registrada no `decision log` com data/hora e responsáveis.
- [ ] Qualquer nota `0` possui ação corretiva com dono e prazo.
- [ ] Se status for `ATTENTION`, há plano explícito D+1/D+3 para fechamento.
- [ ] Se status for `GO`, comunicação D0 está pronta para execução.

## Histórico resumido (opcional)
- Rodada: `YYYY-MM-DD` — Score `__` — Decisão `GO/ATTENTION/NO-GO` — link evidência
