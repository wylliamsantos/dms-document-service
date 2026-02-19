# Observabilidade — painel mínimo (alpha)

Este guia define um painel mínimo no Prometheus/Grafana para operação alfa do `dms-document-service`, usando as métricas já expostas em `/actuator/prometheus`.

## Objetivo

Acompanhar 3 sinais essenciais em tempo real:

1. **Disponibilidade** do serviço
2. **Latência p95** das requisições HTTP
3. **Taxa de erro 5xx**

## Pré-requisitos

- `dms-document-service` com actuator ativo (`/actuator/prometheus`)
- Prometheus coletando o target `document-service`
- Labels padrão esperadas: `job`, `uri`, `method`, `status`

## Painel mínimo (3 widgets)

### 1) Disponibilidade (UP)

**Tipo:** Stat

```promql
max(up{job="document-service"})
```

**Leitura esperada:**
- `1` = serviço disponível
- `0` = indisponível

### 2) Latência p95 HTTP (5m)

**Tipo:** Time series

```promql
histogram_quantile(
  0.95,
  sum by (le) (
    rate(http_server_requests_seconds_bucket{job="document-service", uri!="UNKNOWN"}[5m])
  )
)
```

**Threshold operacional alpha:**
- **warning:** `> 0.8s`
- **critical:** `> 1.2s`

### 3) Taxa de erro 5xx (5m)

**Tipo:** Time series

```promql
sum(rate(http_server_requests_seconds_count{job="document-service", status=~"5.."}[5m]))
/
clamp_min(sum(rate(http_server_requests_seconds_count{job="document-service"}[5m])), 0.001)
```

**Threshold operacional alpha:**
- **warning:** `> 0.03` (3%)
- **critical:** `> 0.05` (5%)

## Alertas recomendados (alinhamento com stack)

- `DmsDocumentServiceDown`: `up == 0`
- `DmsDocumentServiceHighP95Latency`: p95 acima do threshold por janela contínua
- `DmsDocumentServiceHigh5xxRate`: taxa 5xx acima do threshold por janela contínua

> Os nomes acima devem permanecer consistentes com os arquivos de regra em `dms-stack/monitoring/alerts`.

## Evidência mínima por release

Antes de promover release alpha:

1. Confirmar painel carregando métricas em tempo real.
2. Simular indisponibilidade breve e validar alerta de disponibilidade.
3. Registrar timestamp + captura/log no checklist operacional.
