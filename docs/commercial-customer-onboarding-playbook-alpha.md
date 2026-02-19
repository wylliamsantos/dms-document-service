# Playbook de onboarding comercial do cliente (alpha)

Objetivo: padronizar a condução dos primeiros 7 dias após fechamento comercial para reduzir churn inicial e acelerar o primeiro valor percebido.

## Escopo

- Aplicável para novos tenants no alpha.
- Dono do processo: Comercial + Suporte N1, com apoio técnico quando houver bloqueio operacional.
- Janela: D0 a D+7 após confirmação de entrada.

## Meta operacional

- `TTV` (time-to-value) <= 48h (primeiro documento carregado e encontrado via busca).
- `Onboarding success rate` >= 90% no período D0-D+7.
- Nenhum chamado P1 aberto por falha básica de setup.

## Fluxo padrão (D0 a D+7)

1. **D0 — Kickoff comercial**
   - Confirmar plano contratado e limites aplicáveis.
   - Compartilhar pacote inicial (termos, política, checklist de go-live).
   - Registrar responsável do cliente (nome + canal oficial).

2. **D0/D1 — Setup assistido**
   - Executar bootstrap do tenant e validar acesso owner/admin.
   - Confirmar categorias mínimas e upload de 1 documento real de teste.
   - Validar consulta por CPF com retorno esperado.

3. **D1/D2 — Habilitação operacional**
   - Treinar fluxo mínimo: upload, consulta, governança básica.
   - Confirmar entendimento de limites por plano e rota de upgrade.
   - Entregar contatos e SLA de suporte.

4. **D3/D5 — Check de adoção**
   - Validar uso ativo (eventos de upload/consulta).
   - Mapear dúvidas recorrentes e riscos de abandono.
   - Acionar playbook de risco se sinais de baixa adoção.

5. **D7 — Encerramento da fase alpha inicial**
   - Confirmar critérios de sucesso do onboarding.
   - Registrar status final (`SUCCESS`, `PARTIAL`, `AT_RISK`).
   - Definir próximos passos (expansão de uso / correções / plano de ação).

## Critérios mínimos de sucesso

- Tenant criado e usuário owner ativo.
- Pelo menos 1 upload e 1 busca válidos no ambiente oficial.
- Cliente ciente dos limites do plano e canal de suporte.
- Checklist comercial e técnico com evidências anexadas.

## Sinais de risco (gatilhos)

- Sem primeiro upload até D+2.
- Erros recorrentes de autenticação/acesso sem resolução em 24h.
- Baixa resposta do responsável do cliente por >48h.
- Divergência entre expectativa comercial e capacidade alpha atual.

## Ações de mitigação

- Escalonar para suporte/técnico com prioridade `high`.
- Rodar sessão assistida de 30 min para destravar fluxo crítico.
- Realinhar escopo comercial com FAQ e limites oficiais.
- Registrar risco no `commercial-risk-register-alpha.md`.

## Evidências obrigatórias

- Link do checklist de go-live preenchido.
- Evidência de primeiro upload + primeira consulta.
- Registro de comunicação D0 e D7 com status final.
- Decisão documentada no log de decisão comercial.

## Referências

- `docs/commercial-go-live-checklist-alpha.md`
- `docs/commercial-launch-metrics-alpha.md`
- `docs/commercial-risk-register-alpha.md`
- `docs/commercial-go-live-decision-log-alpha.md`
- `docs/commercial-support-playbook-alpha.md`
