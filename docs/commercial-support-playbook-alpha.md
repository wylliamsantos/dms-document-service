# Playbook de Suporte Comercial (Alpha)

## Canais
- Primário: e-mail de suporte (janela comercial UTC).
- Secundário: canal interno de engenharia para incidentes críticos.

## Classificação de chamados
- **P1 (crítico):** indisponibilidade total, perda de acesso generalizada, falha de segurança ativa.
- **P2 (alto):** funcionalidade principal degradada sem workaround simples.
- **P3 (médio):** falha pontual com workaround disponível.
- **P4 (baixo):** dúvidas, melhorias e requests não bloqueantes.

## Objetivos de atendimento (alpha)
- P1: primeiro retorno em até 1h.
- P2: primeiro retorno em até 4h.
- P3: primeiro retorno em até 1 dia útil.
- P4: primeiro retorno em até 2 dias úteis.

## Fluxo de triagem
1. Confirmar tenant afetado, usuário e horário do incidente.
2. Validar status do ambiente (`/actuator/health`, alertas ativos, logs por `traceId`).
3. Classificar severidade e registrar ticket com contexto mínimo.
4. Acionar engenharia quando houver risco de indisponibilidade, segurança ou billing.
5. Atualizar cliente com status, workaround e ETA.

## Runbooks de apoio
- Observabilidade: `docs/observability-panel-minimo.md`
- Segurança operacional: `docs/security-operational-checklist.md`
- Backup/restore: `docs/backup-restore-runbook.md`

## Escalação
- P1/P2 sem mitigação em 30 min: escalar para engenharia responsável.
- Incidente com impacto multi-tenant: escalar imediatamente para liderança técnica.

## Fechamento
- Registrar causa raiz preliminar.
- Documentar ações corretivas e preventivas.
- Atualizar material operacional quando houver aprendizado relevante.
