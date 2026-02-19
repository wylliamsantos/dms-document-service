# Security Operational Checklist (Go-Live Alpha)

Checklist mínimo para validar o item 8 (segurança operacional) em ambiente de stack.

## 1) Rate limit (global + endpoints críticos)

Pré-condições:
- `DMS_SECURITY_RATE_LIMIT_ENABLED=true`
- `DMS_SECURITY_RATE_LIMIT_BACKEND=redis` (em produção)
- Redis disponível

Validação:
1. Limpar chaves `dms:rate-limit:*` no Redis.
2. Executar rajada em endpoint não crítico (`GET /v1/categories/all`) e confirmar bloqueio `429` após o limite global.
3. Executar rajada em endpoint crítico (`POST /v1/documents/multipart` ou `/v1/billing/webhook`) e confirmar bloqueio mais cedo (quota específica).
4. Confirmar presença de chaves com TTL no Redis (`dms:rate-limit:*`).

Critério de aceite:
- `429` observado nos cenários esperados;
- contadores persistidos no Redis com TTL ativo.

## 2) CORS por ambiente

Pré-condições:
- `DMS_SECURITY_ENVIRONMENT=prod`
- `DMS_SECURITY_CORS_FAIL_ON_INSECURE_PRODUCTION_CONFIG=true`

Validação:
1. Configurar `allowed-origins=*` ou `localhost` em `prod`.
2. Confirmar falha de startup (hard fail).
3. Ajustar para origem segura explícita e validar startup normal.

Critério de aceite:
- Configuração insegura em produção não sobe;
- configuração segura sobe normalmente.

## 3) Redirect raiz por ambiente

Pré-condições:
- `DMS_SECURITY_REDIRECT_ROOT_TO_SWAGGER_ENABLED=false` em produção.

Validação:
1. Chamar `GET /`.
2. Confirmar `404` (sem redirect para Swagger em produção).

Critério de aceite:
- root redirect desabilitado em produção.

## 4) Evidências operacionais

Registrar log versionado em `.dms-logs/` contendo:
- comando/tempo do smoke;
- respostas HTTP relevantes;
- snapshot de chaves/TTL no Redis;
- variáveis de ambiente usadas no teste.

## 5) Rollback seguro

Em incidente:
1. reduzir tráfego (WAF/API Gateway) se necessário;
2. ajustar `DMS_SECURITY_RATE_LIMIT_REQUESTS_PER_MINUTE` temporariamente;
3. manter backend Redis habilitado para preservar consistência entre réplicas;
4. registrar incidente e ação corretiva.
