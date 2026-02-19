# Commercial Renewal & Expansion Playbook (Alpha)

## Objetivo
Padronizar a operação de renovação e expansão (upsell/cross-sell) para clientes alpha sem comprometer margem, experiência de suporte e segurança operacional.

## Escopo
- Janela de renovação: D-30 até D+7 da data de vencimento.
- Escopo de expansão: upgrade de plano (STARTER -> PRO -> ENTERPRISE) e add-ons aprovados no alpha.
- Fora de escopo: descontos excepcionais fora de alçada e mudanças contratuais não previstas nos termos alpha.

## Critérios de elegibilidade
- Cliente ativo sem bloqueio jurídico/compliance.
- SLA de suporte dentro da meta no período anterior (últimos 30 dias).
- Sem incidentes P1 abertos nos últimos 7 dias.
- Uso de produto demonstrando aderência mínima (ex.: uso recorrente e onboarding concluído).

## Fluxo operacional (D-30 a D+7)
1. **D-30 a D-21 | Preparação**
   - Revisar scorecard de conta (uso, tickets, inadimplência, risco).
   - Definir proposta base: renovação simples ou expansão recomendada.
2. **D-20 a D-10 | Proposta e alinhamento**
   - Enviar proposta com escopo, plano recomendado e impacto financeiro.
   - Registrar objeções e plano de tratamento no playbook de objeções.
3. **D-9 a D-1 | Negociação final**
   - Validar alçadas para desconto via política de descontos alpha.
   - Confirmar aceite comercial + gatilho operacional para cobrança.
4. **D0 | Fechamento**
   - Executar atualização de plano/termos e validar status de billing.
   - Publicar handoff para suporte/revops com próximos passos.
5. **D+1 a D+7 | Estabilização**
   - Monitorar uso inicial pós-renovação/expansão.
   - Confirmar ausência de regressão em billing/suporte e registrar lições aprendidas.

## Guardrails NO-GO
- Cliente com pendência crítica de segurança/compliance não resolvida.
- Inadimplência ativa sem plano aprovado por RevOps.
- Solicitação de desconto fora de alçada sem aprovação formal.
- Dependência técnica crítica aberta sem mitigação documentada.

## KPIs mínimos da trilha
- Renewal rate (% contas elegíveis renovadas).
- Expansion rate (% contas renovadas com upsell/cross-sell).
- Tempo médio de fechamento de renovação (D-30..D0).
- Churn pós-renovação em 30 dias.
- % renovações com exceção de desconto fora de política.

## Evidências obrigatórias por rodada
- Lista de contas em janela D-30..D+7 com status.
- Registro de decisão (GO/ATTENTION/NO-GO) por conta crítica.
- Evidência de atualização de billing/plano.
- Riscos e ações de contenção documentados no risk register.

## Critério de saída (Go-Live Alpha)
A trilha de renovação/expansão é considerada pronta quando:
- o fluxo acima foi executado em ao menos 1 ciclo completo sem regressão operacional,
- os KPIs mínimos foram coletados e reportados,
- e não há pendências NO-GO abertas para contas críticas na janela corrente.
