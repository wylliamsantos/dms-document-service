# Templates de Comunicação de Suporte (Alpha)

## Objetivo
Padronizar mensagens iniciais de suporte para reduzir tempo de resposta e ruído com clientes alpha.

## 1) Ack inicial (até 15 min)
Assunto: `[DMS Alpha] Recebemos seu chamado #{ticketId}`

Mensagem:
```
Olá, {nome}.

Recebemos seu chamado sobre: {resumo}.

Severidade inicial: {P1|P2|P3|P4}
Próxima atualização prevista: {horario_utc}

Seguimos em investigação e retornamos no próximo update.
```

## 2) Solicitação de dados mínimos
Assunto: `[DMS Alpha] Dados para diagnóstico #{ticketId}`

Mensagem:
```
Para avançarmos no diagnóstico, envie por favor:
- Tenant ID
- Horário aproximado da ocorrência (UTC)
- Endpoint/ação executada
- Trace ID (header X-Trace-Id, se disponível)
- Captura do erro (mensagem/código HTTP)
```

## 3) Atualização intermediária
Assunto: `[DMS Alpha] Update #{ticketId}`

Mensagem:
```
Status atual: {investigando|mitigado_parcialmente|corrigido_em_validacao}
Impacto: {descricao_curta}
Ação em andamento: {acao}
Próxima atualização: {horario_utc}
```

## 4) Encerramento
Assunto: `[DMS Alpha] Encerramento #{ticketId}`

Mensagem:
```
Chamado resolvido.

Causa raiz: {causa}
Correção aplicada: {correcao}
Prevenção: {acao_preventiva}
Horário de normalização (UTC): {horario_utc}
```

## 5) Incidente crítico (P1)
Assunto: `[DMS Alpha][P1] Incidente em andamento`

Mensagem:
```
Identificamos incidente crítico com impacto em {escopo}.
Início estimado: {horario_utc}
Status atual: em mitigação
Próximo update: em até 30 minutos
```
