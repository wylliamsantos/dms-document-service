# Commercial Launch Metrics (Alpha)

## Objetivo
Definir o mínimo de métricas comerciais/operacionais para avaliar readiness e estabilidade nas primeiras semanas de go-live.

## Janela de acompanhamento
- D0 a D+14 após o primeiro cliente pagante.
- Revisão diária (dias úteis) e revisão semanal executiva.

## KPIs mínimos

### Aquisição/Conversão
1. **Trial ativados por semana**
   - Fórmula: `novos trials na semana`
   - Meta alpha: `>= 5`
2. **Conversão trial -> pago (30 dias)**
   - Fórmula: `trials convertidos / trials iniciados`
   - Meta alpha: `>= 20%`

### Receita
3. **MRR inicial (run-rate)**
   - Fórmula: `soma de assinaturas ativas mensais`
   - Meta alpha: crescimento semana a semana (sem regressão por 2 semanas consecutivas).
4. **Inadimplência (past_due rate)**
   - Fórmula: `tenants PAST_DUE / tenants pagos`
   - Alerta: `> 10%`

### Operação/Suporte
5. **Primeira resposta de suporte (P1/P2/P3)**
   - Fonte: playbook de suporte alpha
   - Alerta: qualquer violação de SLA na semana.
6. **Taxa de incidentes P1 por tenant ativo**
   - Fórmula: `incidentes P1 / tenants ativos`
   - Alerta: `> 0.2` na semana.

### Produto/Qualidade
7. **Sucesso no onboarding guiado**
   - Fórmula: `% de tenants que completam bootstrap -> upload -> consulta`
   - Meta alpha: `>= 80%`
8. **Falhas de fluxo crítico (upload/search)**
   - Fórmula: `% de requisições 5xx em endpoints críticos`
   - Alerta: `> 1%` por 1h.

## Gate de saúde comercial (Go semanal)
Classificar semanalmente:
- **GO**: sem alertas vermelhos e no máximo 1 métrica amarela.
- **ATTENTION**: 2-3 métricas amarelas ou 1 vermelha.
- **NO-GO**: >=2 vermelhas ou violação de SLA P1 não mitigada.

## Evidências mínimas
- Snapshot de KPIs da semana (planilha ou dashboard).
- Lista de incidentes e tempos de resposta.
- Ações corretivas abertas com dono e prazo.

## RACI rápido
- **Owner Comercial**: coleta de conversão/MRR.
- **Owner Suporte**: SLAs e incidentes.
- **Owner Produto/Engenharia**: onboarding e estabilidade técnica.
