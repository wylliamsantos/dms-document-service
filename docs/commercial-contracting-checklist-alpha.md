# DMS Alpha — Checklist de Contratação Comercial

## Objetivo
Padronizar a etapa de contratação para reduzir atrito entre Comercial, Financeiro e Suporte, evitando promessa sem cobertura operacional.

## Quando usar
- Toda nova assinatura no alpha (self-serve ou assisted)
- Toda mudança de plano (upgrade/downgrade)
- Toda exceção comercial (desconto fora da régua, prazo especial, SLA diferenciado)

## Checklist obrigatório (pré-fechamento)

### 1) Elegibilidade da conta
- [ ] Tenant identificado e validado (`tenantId` único)
- [ ] Plano alvo definido (TRIAL / STARTER / PRO / ENTERPRISE)
- [ ] Limites técnicos revisados com o cliente (docs/mês, storage, usuários)
- [ ] Stakeholder responsável pelo aceite comercial identificado

### 2) Condições comerciais
- [ ] Preço e periodicidade documentados (mensal/anual)
- [ ] Desconto (se houver) registrado com validade e justificativa
- [ ] Regras de upgrade/downgrade explicadas ao cliente
- [ ] Política de inadimplência comunicada (status `PAST_DUE`, impacto operacional)

### 3) Segurança e compliance do discurso
- [ ] Sem promessa de funcionalidade fora do escopo alpha
- [ ] Sem SLA custom fora do playbook oficial de suporte
- [ ] Termos e privacidade compartilhados com confirmação explícita
- [ ] Riscos conhecidos relevantes (item 7/risco de deduplicação E2E) comunicados quando aplicável

### 4) Prontidão operacional (handoff)
- [ ] Suporte recebeu contexto mínimo do cliente (canal, severidade esperada, janela crítica)
- [ ] RevOps recebeu parâmetros de cobrança e reconciliação
- [ ] Checklist de go-live comercial preenchido
- [ ] Registro no decision log comercial da rodada

## Critérios de bloqueio (NO-GO)
Aplicar `NO-GO` se qualquer item abaixo ocorrer:
- termos não aceitos;
- plano vendido incompatível com limite técnico vigente;
- dependência crítica aberta sem owner/data;
- expectativa contratual incompatível com o estado alpha.

## Evidências mínimas por contratação
- Snapshot do plano + preço + vigência
- Confirmação de aceite de termos/política
- Resultado do gate (`GO/ATTENTION/NO-GO`) com owner responsável
- Link da rodada no `commercial-go-live-decision-log-alpha.md`
