# Backup/Restore Runbook (Alpha)

Este runbook cobre o slice inicial do item 9 (backup/restore testados) para o `dms-document-service`.

## Escopo
- Backup lógico do MongoDB com `mongodump` em arquivo compactado.
- Restore com `mongorestore` (opcionalmente com `--drop`).
- Registro de baseline operacional de RPO/RTO para alpha.

## Pré-requisitos
- `mongodump` e `mongorestore` instalados no host.
- Acesso de rede/credenciais para o MongoDB do ambiente.

## 1) Gerar backup
```bash
cd dms-document-service
MONGO_URI="mongodb://localhost:27017/dms" ./scripts/backup-mongo.sh
```

Saída esperada:
- artefato `./backups/dms-<UTC>.archive.gz`

## 2) Restaurar backup
```bash
cd dms-document-service
MONGO_URI="mongodb://localhost:27017/dms" \
ARCHIVE_PATH="./backups/dms-<UTC>.archive.gz" \
DROP_BEFORE_RESTORE=true \
./scripts/restore-mongo.sh
```

## 3) Validação de restaurabilidade (dry-run)
Antes do restore real, valide o artefato de backup com dry-run:

```bash
cd dms-document-service
MONGO_URI="mongodb://localhost:27017/dms" \
ARCHIVE_PATH="./backups/dms-<UTC>.archive.gz" \
./scripts/verify-restore-mongo.sh
```

Saída esperada:
- `verify-restore done`
- `elapsedSeconds=<n>` para medição operacional de restore (baseline de RTO).

## 4) Validação pós-restore (smoke)
- `GET /actuator/health` => `200`
- Buscar categoria/documento conhecido do tenant de teste e confirmar presença.

## 5) Medir conformidade de RPO/RTO (SLO)
Após executar `verify-restore`, registre a medição com o script abaixo:

```bash
cd dms-document-service
BACKUP_ARTIFACT="./backups/dms-<UTC>.archive.gz" \
VERIFY_ELAPSED_SECONDS="<valor do verify-restore>" \
./scripts/measure-backup-restore-slo.sh
```

Saída esperada:
- `status=WITHIN_TARGET` quando `RPO <= 24h` e `RTO <= 60min`;
- `status=OUT_OF_TARGET` (exit code `2`) quando qualquer alvo for violado.

## Baseline inicial (alpha)
- **RPO alvo:** até 24h (backup diário).
- **RTO alvo:** até 60 min (restore + smoke).

## 6) Drill E2E automatizado (recomendado)
Para executar o ciclo completo em uma tacada (backup -> verify-restore -> restore -> smoke -> medição SLO):

```bash
cd dms-document-service
MONGO_URI="mongodb://localhost:27017/dms" \
HEALTHCHECK_URL="http://localhost:18080/actuator/health" \
./scripts/run-backup-restore-drill.sh
```

### Modo container (fallback)
Quando `mongodump/mongorestore` não estiverem no host:

```bash
cd dms-document-service
MONGO_URI="mongodb://dms:dms@localhost:27017/dms?authSource=dms" \
USE_DOCKER_EXEC=true \
MONGO_CONTAINER_NAME=dms-mongo \
HEALTHCHECK_URL="http://localhost:18080/actuator/health" \
./scripts/run-backup-restore-drill.sh
```

O script gera log versionado em `.dms-logs/backup-restore-drill-<UTC>.log`.

## Fallback operacional (sem `mongodump`/`mongorestore` no host)
Quando os binários não estiverem instalados no host, execute backup/restore usando o container `dms-mongo` com `docker exec -i`:

```bash
# backup
cd dms-document-service
ARTIFACT="./backups/dms-$(date -u +%Y%m%dT%H%M%SZ).archive.gz"
docker exec dms-mongo sh -lc \
  'mongodump --uri="mongodb://dms:dms@localhost:27017/dms?authSource=dms" --gzip --archive' \
  > "$ARTIFACT"

# verify-restore (dry-run)
docker exec -i dms-mongo sh -lc \
  'mongorestore --uri="mongodb://dms:dms@localhost:27017/dms?authSource=dms" --gzip --archive --dryRun' \
  < "$ARTIFACT"

# restore real (com drop)
docker exec -i dms-mongo sh -lc \
  'mongorestore --uri="mongodb://dms:dms@localhost:27017/dms?authSource=dms" --gzip --archive --drop' \
  < "$ARTIFACT"
```

## Evidência operacional (stack local)
- Execução validada em `2026-02-19T11:37Z` com log versionado em:
  - `.dms-logs/backup-restore-stack-20260219T113728Z.log`
- Resultado:
  - `verify-restore` (dry-run) concluído com sucesso (`elapsedSeconds=1`);
  - `restore` real com `--drop` concluído sem falhas (`39 documents restored`);
  - smoke pós-restore: `GET /actuator/health` => **200**.

> Observação: valores serão refinados após automação recorrente e medição em ambiente de produção.
