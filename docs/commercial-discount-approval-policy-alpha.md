# Política de aprovação de descontos (alpha)

## Objetivo
Padronizar concessão de descontos no go-live alpha para evitar erosão de margem, overpromise comercial e desalinhamento com capacidade operacional.

## Escopo
- Novas vendas alpha (self-serve assistido e vendas assistidas).
- Renovações no período D0–D+30 do alpha.
- Não cobre acordos enterprise fora do pacote alpha (trilha separada com jurídico/financeiro).

## Regras base
- **Preço de tabela** é padrão.
- Desconto só pode ser concedido com justificativa documentada e aprovação conforme matriz abaixo.
- Desconto nunca pode compensar ausência de requisito técnico crítico (segurança, compliance, deduplicação fim-a-fim).
- Qualquer desconto deve manter alinhamento com limites por plano já publicados.

## Faixas de aprovação
| Faixa de desconto | Aprovação mínima | Evidência obrigatória |
|---|---|---|
| 0% a 10% | Sales owner + registro no CRM | Motivo comercial + prazo da oferta |
| >10% a 20% | Sales owner + liderança comercial | Motivo + impacto em margem + plano de expansão |
| >20% a 30% | Liderança comercial + Finance | Business case, payback e riscos |
| >30% | **NO-GO por padrão no alpha** | Exceção só via comitê executivo |

## Guardrails (NO-GO automático)
- Pedido de desconto atrelado a SLA/feature fora do escopo alpha.
- Cliente com risco alto de inadimplência sem mitigação aprovada.
- Pedido condicionado à remoção de cláusulas mínimas de segurança/privacidade.
- Pedido de desconto cumulativo com condições promocionais já vigentes.

## SLA de decisão
- Até 10%: mesmo dia útil.
- 10–20%: até 1 dia útil.
- 20–30%: até 2 dias úteis.
- Exceções >30%: sem SLA no alpha; tratativa extraordinária.

## Template de registro (mínimo)
- Conta/oportunidade:
- Plano alvo:
- Desconto solicitado:
- Prazo da condição:
- Justificativa comercial:
- Risco principal:
- Aprovações:
- Decisão final (GO/ATTENTION/NO-GO):

## Métrica de controle (semanal)
- % de deals com desconto por faixa.
- Desconto médio ponderado por MRR.
- Taxa de win por faixa de desconto.
- Churn de contas com desconto vs sem desconto.

## Revisão
Revisar política a cada rodada semanal de go-live enquanto durar o alpha.
