# DMS Alpha — FAQ comercial (vendas/pré-vendas)

## Objetivo
Padronizar respostas para objeções recorrentes durante qualificação e negociação do alpha, alinhadas ao escopo técnico real já entregue.

## Perguntas frequentes

### 1) O produto já é multi-tenant de verdade?
**Resposta curta:** Sim, com isolamento por `tenant_id` validado em testes e smokes A/B.

**Pontos de apoio:**
- Requisições com mismatch token/header são bloqueadas.
- Fluxo de categorias/documentos/busca validado entre tenants distintos sem vazamento.

### 2) Como funciona controle de acesso por perfil?
**Resposta curta:** RBAC mínimo de produção com perfis `owner/admin/reviewer/viewer`.

**Pontos de apoio:**
- Enforcement por ação nos serviços centrais.
- Compatibilidade legada mantida onde necessário para transição.

### 3) Já existe onboarding sem suporte?
**Resposta curta:** Sim, com fluxo guiado até primeiro documento no frontend e bootstrap backend.

**Pontos de apoio:**
- Jornada: bootstrap -> categorias -> upload -> consulta.
- Evidência de execução contínua no ambiente de stack.

### 4) Quais limites existem por plano?
**Resposta curta:** Limites já ativos para docs/mês, storage e usuários, com mensagem de upgrade.

**Pontos de apoio:**
- Bloqueio elegante quando cota é atingida.
- Checkpoint para provisionamento de usuário antes da criação.

### 5) O billing está pronto para produção?
**Resposta curta:** Funcional para alpha (trial, assinatura, webhook idempotente assinado, refresh de assinatura).

**Transparência recomendada:**
- Integrações avançadas de cobrança podem evoluir por fornecedor no pós-alpha.

### 6) Quais garantias operacionais já existem?
**Resposta curta:** Observabilidade, segurança operacional e runbook de backup/restore foram implantados.

**Pontos de apoio:**
- Métricas e alertas baseados em latência/erro/disponibilidade.
- Rate limit por tenant/IP e configuração por ambiente (CORS/redirect).
- Drill de backup/restore com medição de RTO/RPO.

## Mensagens de posicionamento (usar em call)
- **Valor:** "Você entra no alpha com controles reais de isolamento, acesso e operação — não é demo de fachada."
- **Risco controlado:** "O go-live alpha usa checklist de Go/No-Go e evidências objetivas por rodada."
- **Transparência:** "Onde ainda há pendência de governança/review, mostramos explicitamente e não prometemos antes do merge."

## Limites de discurso (não prometer)
- Não prometer SLA contratual acima do definido no playbook alpha.
- Não prometer disponibilidade multi-região.
- Não prometer integrações de billing fora do escopo já versionado.

## Referências
- `docs/commercial-pricing-alpha.md`
- `docs/commercial-terms-alpha.md`
- `docs/commercial-privacy-policy-alpha.md`
- `docs/commercial-support-playbook-alpha.md`
- `docs/commercial-go-live-checklist-alpha.md`
- `docs/commercial-gtm-handoff-alpha.md`
