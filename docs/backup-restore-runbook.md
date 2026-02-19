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

## 3) Validação pós-restore (smoke)
- `GET /actuator/health` => `200`
- Buscar categoria/documento conhecido do tenant de teste e confirmar presença.

## Baseline inicial (alpha)
- **RPO alvo:** até 24h (backup diário).
- **RTO alvo:** até 60 min (restore + smoke).

> Observação: valores serão refinados após automação recorrente e medição em ambiente de produção.
