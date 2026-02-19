# Plano de comunicação de lançamento comercial (alpha)

## Objetivo
Padronizar o que, quando e como comunicar durante a janela de go-live alpha para reduzir ruído, acelerar resposta a risco e manter discurso consistente com o pacote comercial aprovado.

## Janelas e gatilhos

### T-7 a T-1 (pré-go-live)
- Revisar status no `docs/commercial-go-live-decision-log-alpha.md`.
- Confirmar sign-off por área em `docs/commercial-approval-matrix-alpha.md`.
- Validar FAQ e limites de discurso (`docs/commercial-sales-faq-alpha.md`).
- Preparar canais e responsáveis (comercial, suporte, produto).

### D0 (dia de go-live)
- Publicar mensagem interna de **GO** (ou **ATTENTION/NO-GO**) com referência ao log de decisão.
- Comunicar ao time comercial:
  - oferta ativa (pricing válido);
  - escopo permitido no alpha;
  - principais riscos abertos e mitigação.
- Comunicar ao suporte:
  - playbook ativo;
  - SLA operacional da janela;
  - template padrão de resposta inicial.

### D+1 a D+14 (estabilização)
- Resumo diário curto com KPIs do pacote de métricas (`docs/commercial-launch-metrics-alpha.md`).
- Atualização imediata em caso de incidente P1/P2 ou mudança de gate.
- Revisão semanal de risco/decisão (`GO/ATTENTION/NO-GO`).

## Canais oficiais e donos
- **Canal interno de operação (engenharia + produto):** atualização técnica e riscos.
- **Canal comercial:** posicionamento, objeções, status de disponibilidade.
- **Canal de suporte:** incidentes, fila, SLAs e orientações de resposta.

> Definir os IDs reais dos canais no ambiente operacional antes de D0.

## Templates mínimos

### 1) Anúncio interno de GO
```text
[GO-LIVE ALPHA] Status: GO
Escopo liberado: <resumo>
Riscos conhecidos: <top 3>
Mitigações ativas: <resumo>
Próxima revisão: <data/hora UTC>
Referências: decision-log + approval-matrix + launch-metrics
```

### 2) Atualização de risco (ATTENTION)
```text
[GO-LIVE ALPHA] Status: ATTENTION
Risco: <descrição objetiva>
Impacto potencial: <receita/operação/suporte>
Mitigação em curso: <ação + ETA>
Owner: <área/pessoa>
Próximo checkpoint: <data/hora UTC>
```

### 3) Aviso de NO-GO
```text
[GO-LIVE ALPHA] Status: NO-GO
Motivo: <critério não atendido>
Evidência: <link para métrica/log/check>
Plano de retorno: <ações + responsáveis + ETA>
Nova janela de decisão: <data/hora UTC>
```

## Regras de comunicação
- Nunca prometer funcionalidade fora do escopo aprovado no alpha.
- Sempre referenciar artefato versionado ao comunicar decisão de gate.
- Separar fato (evidência) de hipótese (investigação em andamento).
- Em incidente ativo, priorizar cadência previsível de atualização (ex.: a cada 30 min).

## Critério de pronto
Considera-se a comunicação de lançamento pronta quando:
1. Existe mensagem padrão para GO/ATTENTION/NO-GO;
2. Há dono por canal e cadência definida para D0–D+14;
3. O pacote comercial mínimo está referenciado no README;
4. O primeiro ciclo real de atualização foi registrado no log de decisão.
