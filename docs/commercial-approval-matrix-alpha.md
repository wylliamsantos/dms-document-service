# Matriz de Aprovação Comercial (Alpha)

Este artefato formaliza quem aprova o pacote mínimo comercial antes de declarar **GO** no alpha.

## Objetivo

Garantir que o go-live comercial não avance sem validação explícita dos responsáveis de negócio, operação e produto.

## Escopo de aprovação

A aprovação cobre os artefatos:

- `docs/commercial-pricing-alpha.md`
- `docs/commercial-terms-alpha.md`
- `docs/commercial-privacy-policy-alpha.md`
- `docs/commercial-support-playbook-alpha.md`
- `docs/commercial-go-live-checklist-alpha.md`
- `docs/commercial-support-templates-alpha.md`
- `docs/commercial-gtm-handoff-alpha.md`
- `docs/commercial-sales-faq-alpha.md`
- `docs/commercial-launch-metrics-alpha.md`
- `docs/commercial-risk-register-alpha.md`
- `docs/commercial-go-live-decision-log-alpha.md`

## Matriz de sign-off

| Área | Responsável | Critério mínimo para aprovar | Evidência obrigatória | Status |
| --- | --- | --- | --- | --- |
| Produto | Product Owner | Escopo comercial condizente com capacidade técnica do alpha | Checklist de go-live preenchido | PENDENTE |
| Comercial | Líder Comercial | Pricing, FAQ e limites sem overpromise | Revisão de pricing + FAQ assinada | PENDENTE |
| Suporte | Líder de Suporte | Playbook e templates prontos para operação D0 | Simulação de incidente P1/P2 registrada | PENDENTE |
| Operações | Responsável SRE/Plataforma | Alertas, backup/restore e runbooks utilizáveis | Evidência operacional anexada no log de decisão | PENDENTE |
| Segurança/Privacidade | Responsável de Compliance | Política de privacidade e termos minimamente aderentes ao alpha | Revisão documental com ressalvas registradas | PENDENTE |

## Regra de gate

- **GO**: todas as áreas acima com status **APROVADO** (ou **APROVADO COM RESSALVAS** com plano e prazo).
- **ATTENTION**: até 2 áreas com ressalva ativa e plano de mitigação datado.
- **NO-GO**: qualquer área crítica sem aprovação (Suporte, Operações, Segurança/Privacidade).

## Operação da cerimônia

1. Atualizar status desta matriz antes da reunião de decisão.
2. Registrar o resultado final em `docs/commercial-go-live-decision-log-alpha.md`.
3. Se houver ressalvas, criar ação com dono e prazo no mesmo log de decisão.
