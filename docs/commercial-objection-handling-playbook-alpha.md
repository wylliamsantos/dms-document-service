# Commercial Objection Handling Playbook (Alpha)

Objetivo: padronizar respostas comerciais para objeções críticas sem comprometer escopo técnico, segurança ou SLA real do alpha.

## 1) Princípios

- Nunca prometer roadmap sem owner técnico aprovado.
- Nunca prometer SLA/SLO além do que está nos artefatos de prontidão.
- Converter objeções em hipóteses testáveis com prazo e responsável.
- Registrar toda objeção relevante no log de decisão comercial (`commercial-go-live-decision-log-alpha.md`).

## 2) Objeções críticas e resposta padrão

### 2.1 "Consigo produção full já no dia 1?"
Resposta-base:
- O alpha é controlado por gates GO/ATTENTION/NO-GO.
- Entrada em produção depende de scorecard + matriz de aprovação.
- Podemos executar piloto assistido com escopo e limites claros.

### 2.2 "Vocês garantem zero indisponibilidade?"
Resposta-base:
- Não prometemos indisponibilidade zero.
- Temos baseline de observabilidade e alertas ativos para reduzir MTTR.
- Para carga crítica, definimos plano de contingência e janela operacional.

### 2.3 "Quero desconto alto agora"
Resposta-base:
- Descontos seguem política de alçada (`commercial-discount-approval-policy-alpha.md`).
- Exceções precisam justificativa econômica e aprovação formal.
- Sem aprovação, proposta segue tabela vigente.

### 2.4 "Preciso de feature fora do escopo"
Resposta-base:
- Classificamos como "gap crítico" ou "melhoria pós-go-live".
- Se crítico para assinatura: abrir trilha com owner e data alvo.
- Se não crítico: registrar no backlog e manter proposta padrão.

## 3) Fluxo operacional (máx. 24h)

1. Capturar objeção (contexto, impacto, urgência).
2. Classificar risco: comercial, técnico, jurídico, suporte.
3. Acionar owner da área e proposta de resposta.
4. Retornar ao cliente com decisão objetiva (GO/ATTENTION/NO-GO).
5. Registrar no decision log com evidências e próximo passo.

## 4) Critérios de bloqueio (NO-GO)

- Exigência contratual incompatível com política vigente.
- Dependência de capability não validada no alpha.
- Pressão por compromisso de SLA sem cobertura operacional.

## 5) Evidências mínimas por rodada

- Objeções abertas/fechadas por severidade.
- Tempo médio de resposta de objeção crítica.
- Taxa de conversão pós-tratamento de objeção.
- Top 3 objeções recorrentes + ação preventiva.
