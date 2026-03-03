# Go/No-Go Checkpoint — Alpha (2026-02-28)

## Objetivo
Conduzir o ritual final de decisão de lançamento Alpha após fechamento da trilha técnica (itens 1–10) e merge das PRs críticas.

## Janela sugerida
- Data: 2026-02-28
- Horário (UTC): 20:30
- Duração: 30 minutos
- Dono: PM/Eng Lead

## Participantes obrigatórios
- Produto
- Engenharia
- Operações/SRE
- Comercial/CS (representante)

## Gate de decisão
Usar o scorecard em `docs/commercial-go-live-scorecard-alpha.md`.

- **GO**: score >= 85 e nenhum bloqueio crítico aberto
- **ATTENTION**: score 70–84 com plano corretivo datado
- **NO-GO**: score < 70 ou risco crítico sem mitigação

## Evidências obrigatórias (pré-reunião)
1. `dms-go-live-alpha-roadmap.md` com itens 1–10 concluídos
2. `dms-document-service#24` em estado `MERGED`
3. CI verde na trilha principal (`./gradlew test --no-daemon`)
4. Checklist comercial mínimo completo
5. Plano de comunicação pronto (`docs/commercial-launch-communications-alpha.md`)

## Ata (preencher durante a reunião)
- Decisão final: `GO | ATTENTION | NO-GO`
- Score final:
- Bloqueios remanescentes:
- Ações e donos:
- Janela de comunicação:

## Comunicação pós-decisão
- GO: disparar comunicação interna e janela de monitoramento D0–D+2
- ATTENTION: comunicar pendências + nova data de checkpoint
- NO-GO: registrar plano corretivo e congelar anúncio externo
