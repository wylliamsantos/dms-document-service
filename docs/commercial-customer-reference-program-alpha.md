# Programa de Referências de Clientes (alpha)

## Objetivo
Estabelecer um fluxo mínimo e repetível para transformar clientes com adoção saudável em referências comerciais sem gerar overpromise.

## Critérios de elegibilidade (entrada)
Um cliente só entra no programa quando TODOS os critérios abaixo forem verdadeiros na última janela de 14 dias:

1. Onboarding concluído (fluxo principal funcionando no tenant).
2. Pelo menos 1 caso de uso em produção ativo.
3. Sem incidente P1 aberto.
4. SLA de suporte dentro da meta da fase alpha.
5. Sponsor do cliente explícito (nome, cargo e canal principal).

Se qualquer item falhar, status automático: `NO-GO`.

## Fluxo operacional (D0 a D+21)

### D0 — Pré-validação interna
- Comercial confirma elegibilidade no scorecard.
- Suporte confirma ausência de bloqueios críticos.
- Produto valida que limites conhecidos do alpha foram comunicados.

Saída obrigatória: registro no log de decisão (`GO` ou `ATTENTION`).

### D+1 a D+7 — Convite e alinhamento
- Enviar convite formal para participação como referência.
- Explicar claramente o escopo da referência (call, quote, case curto).
- Registrar consentimento explícito do cliente.

### D+8 a D+14 — Produção dos ativos
- Coletar narrativa (problema, contexto, resultado observável).
- Revisar texto com guardrails de compliance (sem dados sensíveis, sem promessas de roadmap).
- Aprovação interna de Comercial + Produto antes de publicar.

### D+15 a D+21 — Publicação controlada
- Publicar ativo aprovado (interno ou externo conforme consentimento).
- Atualizar evidência no pacote de go-live comercial.
- Agendar revisão pós-uso (aprendizados e riscos).

## Guardrails (NO-GO)
Bloquear publicação imediata quando houver:
- Incidente P1/P0 aberto no cliente;
- Divergência entre contrato e narrativa comercial;
- Citação de funcionalidades não disponíveis no alpha;
- Falta de consentimento explícito e rastreável.

## KPIs mínimos
- `eligible_accounts_total`
- `reference_invites_sent_total`
- `references_published_total`
- `reference_cycle_time_days` (D0 até publicação)
- `references_blocked_no_go_total`

## Evidências obrigatórias por referência
1. Registro de elegibilidade com data.
2. Consentimento do cliente.
3. Versão aprovada do material.
4. Link/publicação final.
5. Retro de aprendizados em até 7 dias após publicação.

## RACI mínimo
- **Responsável:** Comercial (owner da referência)
- **Aprovadores:** Produto + Suporte
- **Consultados:** Jurídico/Compliance (quando aplicável)
- **Informados:** Liderança Go-to-Market
