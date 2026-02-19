# Churn & Retention Playbook (Alpha)

## Objetivo
Padronizar a resposta comercial-operacional para sinais de risco de churn no período D0-D+30 do alpha, reduzindo perda de receita e acelerando recuperação de contas em risco.

## Escopo
- Clientes alpha em trial, `STARTER` e `PRO`
- Sinais de risco vindos de produto, billing e suporte
- Ações de contenção de curto prazo (até 7 dias)

## Sinais de risco (gatilhos)

### Risco Alto (acionar em até 4h)
- Falha em fluxo crítico por mais de 2h (upload/consulta/onboarding)
- Incidente P1 sem ETA claro
- Cobrança indevida confirmada
- Solicitação explícita de cancelamento

### Risco Médio (acionar em até 1 dia útil)
- Queda >50% no uso semanal
- Múltiplos chamados sobre mesma dor sem resolução definitiva
- Conta em `PAST_DUE` por mais de 3 dias

### Risco Baixo (acionar em até 2 dias úteis)
- Feedback neutro/negativo recorrente sem escalonamento
- Inatividade de onboarding por >7 dias após ativação

## Tática por janela

### 0-24h (containment)
1. Registrar caso no log de decisão/comercial.
2. Definir owner único (CS ou suporte líder).
3. Enviar comunicação de reconhecimento + plano imediato.
4. Se necessário, aplicar medida tática temporária (ex.: extensão de trial, priorização de suporte, acompanhamento diário).

### 24-72h (recovery)
1. Confirmar causa raiz com time técnico.
2. Apresentar plano de correção com prazo e checkpoints.
3. Revalidar fluxo crítico com o cliente.
4. Documentar risco residual e novo checkpoint.

### 3-7 dias (stabilization)
1. Confirmar retorno ao baseline de uso.
2. Validar satisfação mínima do sponsor.
3. Encerrar caso com resumo de causa, ação e prevenção.

## Playbook de comunicação
- Mensagem inicial: até 4h (risco alto) / 1 dia útil (médio)
- Atualizações: diária para risco alto, a cada 48h para médio
- Encerramento: resumo executivo + próximos passos

## Alçadas e decisão
- **CS**: conduz relacionamento e plano de recuperação
- **Suporte**: executa trilha operacional e coleta evidências
- **Produto/Engenharia**: prioriza correções quando risco alto
- **Comercial**: aprova concessões (desconto, extensão, crédito)

## Evidências mínimas por caso
- Conta/tenant afetado
- Gatilho que iniciou o caso
- Linha do tempo de ações
- Resultado (recuperado, em observação, churn)
- Aprendizado e ação preventiva

## Critério de sucesso
- 100% dos casos de risco alto com owner e plano em até 4h
- >=80% dos casos médios recuperados sem churn em 7 dias
- Tempo médio de resposta dentro do SLA definido neste playbook
