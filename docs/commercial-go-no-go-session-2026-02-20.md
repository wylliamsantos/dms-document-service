# Go/No-Go Checkpoint — Alpha (2026-02-20)

## Objetivo
Registrar a decisão formal de lançamento do Alpha com trilha auditável entre Produto, Engenharia e Operações.

## Janela sugerida
- Data: 2026-02-20
- Horário (UTC): 14:00
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
1. Roadmap técnico com itens 1–10 concluídos
2. CI verde nas trilhas principais
3. Evidência de backup/restore e idempotência downstream
4. Checklist comercial mínima completa
5. Plano de comunicação pronto (`docs/commercial-launch-communications-alpha.md`)

## Ata (preencher durante a reunião)
- Decisão final: `GO | ATTENTION | NO-GO`
- Score final:
- Bloqueios remanescentes:
- Ações e donos:
- Data/hora de reavaliação (se ATTENTION/NO-GO):

## Comunicação pós-decisão
- GO: disparar comunicação interna e janela de monitoramento D0–D+2
- ATTENTION: comunicar pendências + nova data de checkpoint
- NO-GO: registrar rollback/plano corretivo e congelar anúncio externo
