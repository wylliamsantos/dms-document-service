# Playbook de conversão Trial → Pago (alpha)

## Objetivo
Padronizar a operação comercial para converter tenants em trial para plano pago sem overpromise, com critérios claros de elegibilidade e handoff para cobrança/suporte.

## Janela operacional
- **D-7 a D-1 (pré-vencimento do trial):** qualificação + plano recomendado.
- **D0 (vencimento):** proposta final + confirmação de decisão.
- **D+1 a D+7:** recuperação de oportunidades em risco.

## Segmentação mínima
- **Quente:** uso ativo + sinal de valor (upload + busca + onboarding concluído).
- **Morno:** uso parcial, sem bloqueio técnico crítico.
- **Frio:** baixo uso e/ou pendência de segurança/compliance aberta.

## Critérios de elegibilidade para oferta
1. Tenant com onboarding funcional (documento publicado + consulta com sucesso).
2. Ausência de incidentes P1 abertos nos últimos 7 dias.
3. Limites por plano comunicados (docs/mês, storage, usuários).
4. Expectativa comercial alinhada ao pacote alpha (sem custom fora do roadmap).

## Sequência operacional recomendada
1. **Preparar contexto:** revisar scorecard comercial, risco e histórico de suporte.
2. **Contato de conversão:** apresentar plano recomendado + guardrails.
3. **Validação de objeções:** usar playbook de objeções e política de descontos.
4. **Decisão:** registrar GO/ATTENTION/NO-GO no decision log.
5. **Handoff:** acionar RevOps para cobrança e suporte para acompanhamento D+7.

## Mensagens mínimas obrigatórias ao cliente
- Escopo do plano escolhido e limites aplicáveis.
- Data de início de cobrança e política de inadimplência.
- Canais de suporte e SLA de resposta.
- Próximo checkpoint de sucesso (até 7 dias).

## Indicadores de sucesso (D0-D+7)
- Taxa de conversão trial→pago por coorte.
- Tempo médio de decisão (primeiro contato → aceite).
- Percentual de deals com desconto fora da política (deve ser 0 no alpha).
- Reabertura de chamados P1 no pós-conversão (deve permanecer 0).

## Critérios de bloqueio (NO-GO)
- Solicitação de promessa fora do escopo alpha.
- Dependência crítica sem owner definido (produto/operação).
- Inconsistência de cobrança/assinatura sem mitigação ativa.

## Evidências mínimas por conversão
- Registro no decision log (`GO/ATTENTION/NO-GO`).
- Plano aprovado e justificativa comercial.
- Confirmação de handoff para RevOps + suporte.
- Resultado D+7 (saúde do tenant após ativação paga).
