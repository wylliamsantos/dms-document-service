package br.com.dms.service;

import br.com.dms.controller.response.DocumentInsightResponse;
import br.com.dms.controller.response.DocumentRagContextResponse;
import br.com.dms.controller.response.InsightSignalResponse;
import br.com.dms.controller.response.MetadataSuggestionResponse;
import br.com.dms.controller.response.RagContextChunkResponse;
import br.com.dms.domain.mongodb.DmsDocument;
import br.com.dms.exception.DmsDocumentNotFoundException;
import br.com.dms.exception.TypeException;
import br.com.dms.repository.mongo.DmsDocumentRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Map.entry;

@Service
public class DocumentInsightService {

    private static final Map<String, String> IMPORTANT_METADATA_KEYS = Map.ofEntries(
            entry("cpf", "cpf"),
            entry("cnpj", "cnpj"),
            entry("numero", "numero"),
            entry("valor", "valor"),
            entry("vencimento", "vencimento"),
            entry("data_emissao", "data_emissao"),
            entry("nome", "nome")
    );

    private final AiMetadataSuggestionService aiMetadataSuggestionService;
    private final TenantContextService tenantContextService;
    private final DmsDocumentRepository dmsDocumentRepository;
    private final MeterRegistry meterRegistry;
    private final boolean ragEnabled;
    private final Set<String> ragEnabledTenants;
    private final Set<String> ragEnabledCategories;

    public DocumentInsightService(AiMetadataSuggestionService aiMetadataSuggestionService,
                                  TenantContextService tenantContextService,
                                  DmsDocumentRepository dmsDocumentRepository,
                                  MeterRegistry meterRegistry,
                                  @Value("${dms.ai.rag.document.enabled:false}") boolean ragEnabled,
                                  @Value("${dms.ai.rag.document.enabled-tenants:}") String ragEnabledTenants,
                                  @Value("${dms.ai.rag.document.enabled-categories:}") String ragEnabledCategories) {
        this.aiMetadataSuggestionService = aiMetadataSuggestionService;
        this.tenantContextService = tenantContextService;
        this.dmsDocumentRepository = dmsDocumentRepository;
        this.meterRegistry = meterRegistry;
        this.ragEnabled = ragEnabled;
        this.ragEnabledTenants = parseEnabledTenants(ragEnabledTenants);
        this.ragEnabledCategories = parseEnabledCategories(ragEnabledCategories);
    }

    public DocumentInsightResponse getInsight(String documentId, Optional<String> version) {
        String tenantId = tenantContextService.requireTenantId();
        MetadataSuggestionResponse suggestion = aiMetadataSuggestionService.suggest(documentId, version);
        DmsDocument document = resolveDocument(documentId, tenantId);

        Map<String, Object> persistedMetadataPreview = extractMetadataPreview(document);
        Map<String, Object> importantPersistedMetadata = extractImportantPersistedMetadata(document);
        Map<String, Object> resolvedMetadata = new LinkedHashMap<>();
        if (suggestion.getSuggestedMetadata() != null) {
            resolvedMetadata.putAll(suggestion.getSuggestedMetadata());
        }
        if (resolvedMetadata.isEmpty()) {
            resolvedMetadata.putAll(persistedMetadataPreview);
        }

        String confidenceBand = resolveConfidenceBand(suggestion.getConfidence());
        Counter.builder("dms.ai.document.insight.requests")
                .description("Insight requests by tenant/confidence/source")
                .tag("tenant", sanitizeTenantTag(tenantId))
                .tag("confidence_band", confidenceBand)
                .tag("source", sanitizeMetricTag(suggestion.getSource(), "unknown"))
                .register(meterRegistry)
                .increment();

        return DocumentInsightResponse.builder()
                .documentId(suggestion.getDocumentId())
                .version(suggestion.getVersion())
                .summary(suggestion.getSummary())
                .keyMetadata(resolvedMetadata)
                .warnings(suggestion.getConsistencyWarnings())
                .confidence(suggestion.getConfidence())
                .confidenceBand(confidenceBand)
                .source(suggestion.getSource())
                .generatedAt(Instant.now().toString())
                .signals(resolveSignals(suggestion))
                .persistedMetadataPreview(persistedMetadataPreview)
                .importantPersistedMetadata(importantPersistedMetadata)
                .ocrStats(resolveOcrStats(document))
                .build();
    }

    private String resolveConfidenceBand(double confidence) {
        if (confidence >= 0.85d) {
            return "HIGH";
        }
        if (confidence >= 0.60d) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private List<InsightSignalResponse> resolveSignals(MetadataSuggestionResponse suggestion) {
        String source = StringUtils.lowerCase(StringUtils.defaultString(suggestion.getSource()));
        return List.of(
                InsightSignalResponse.builder().signal("ocr").description("Trechos OCR usados na inferência").active(source.contains("ocr")).build(),
                InsightSignalResponse.builder().signal("metadata").description("Metadados estruturados usados").active(source.contains("metadata")).build(),
                InsightSignalResponse.builder().signal("heuristics").description("Regras heurísticas aplicadas").active(source.contains("heuristic")).build(),
                InsightSignalResponse.builder().signal("filename").description("Nome do arquivo usado como sinal").active(source.contains("filename") || source.contains("name")).build()
        );
    }

    private DmsDocument resolveDocument(String documentId, String tenantId) {
        return dmsDocumentRepository.findByIdAndTenantId(documentId, tenantId).orElse(null);
    }

    private Map<String, Object> extractMetadataPreview(DmsDocument document) {
        if (document == null || document.getMetadata() == null || document.getMetadata().isEmpty()) {
            return Map.of();
        }

        LinkedHashMap<String, Object> preview = new LinkedHashMap<>();
        document.getMetadata().forEach((key, value) -> {
            if (preview.size() >= 6 || value == null) {
                return;
            }
            if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                preview.put(key, value);
            }
        });
        return preview;
    }

    private Map<String, Object> extractImportantPersistedMetadata(DmsDocument document) {
        if (document == null || document.getMetadata() == null || document.getMetadata().isEmpty()) {
            return Map.of();
        }

        LinkedHashMap<String, Object> important = new LinkedHashMap<>();
        document.getMetadata().forEach((key, value) -> {
            String normalizedKey = StringUtils.lowerCase(StringUtils.trimToEmpty(key));
            if (value == null || !IMPORTANT_METADATA_KEYS.containsKey(normalizedKey)) {
                return;
            }
            if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                important.put(IMPORTANT_METADATA_KEYS.get(normalizedKey), value);
            }
        });

        return important;
    }

    private Map<String, Object> resolveOcrStats(DmsDocument document) {
        String ocrText = document == null ? "" : StringUtils.trimToEmpty(document.getOcrText());
        if (StringUtils.isBlank(ocrText)) {
            return Map.of();
        }

        int lines = (int) ocrText.lines().map(StringUtils::trim).filter(StringUtils::isNotBlank).count();
        int words = ocrText.trim().split("\\s+").length;

        LinkedHashMap<String, Object> stats = new LinkedHashMap<>();
        stats.put("chars", ocrText.length());
        stats.put("lines", lines);
        stats.put("words", words);
        return stats;
    }

    public DocumentRagContextResponse getRagContextSkeleton(String documentId, Optional<String> version) {
        String tenantId = tenantContextService.requireTenantId();
        Instant startedAt = Instant.now();

        if (!ragEnabled) {
            return buildRagResponse(documentId, version, tenantId, "", false, "DISABLED",
                    "RAG de documento desabilitado por feature flag (dms.ai.rag.document.enabled=false).", List.of(), startedAt);
        }

        if (!ragEnabledTenants.isEmpty() && !ragEnabledTenants.contains(tenantId)) {
            return buildRagResponse(documentId, version, tenantId, "", false, "TENANT_DISABLED",
                    "RAG de documento desabilitado para o tenant atual (allowlist não inclui este tenant).", List.of(), startedAt);
        }

        DmsDocument document = dmsDocumentRepository.findByIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> new DmsDocumentNotFoundException("Document not found", TypeException.VALID));
        String category = StringUtils.trimToEmpty(document.getCategory());

        if (!ragEnabledCategories.isEmpty()) {
            boolean allowedCategory = ragEnabledCategories.contains(StringUtils.lowerCase(category));
            if (!allowedCategory) {
                return buildRagResponse(documentId, version, tenantId, category, false, "CATEGORY_DISABLED",
                        "RAG de documento desabilitado para a categoria atual (allowlist por categoria).", List.of(), startedAt);
            }
        }

        List<RagContextChunkResponse> chunks = buildChunks(document);
        return buildRagResponse(documentId, version, tenantId, category, true, "READY",
                chunks.isEmpty() ? "Sem chunks de OCR disponíveis para este documento." : "Contexto RAG local carregado.",
                chunks,
                startedAt);
    }

    private List<RagContextChunkResponse> buildChunks(DmsDocument document) {
        String ocrText = StringUtils.trimToEmpty(document.getOcrText());
        if (StringUtils.isBlank(ocrText)) {
            return List.of();
        }

        List<RagContextChunkResponse> chunks = new ArrayList<>();
        String[] paragraphs = ocrText.replace("\r", "\n").split("\\n\\s*\\n");
        for (String paragraph : paragraphs) {
            String clean = StringUtils.normalizeSpace(paragraph);
            if (StringUtils.isBlank(clean)) {
                continue;
            }

            String excerpt = clean.substring(0, Math.min(clean.length(), 400));
            double score = Math.min(1.0d, excerpt.length() / 400.0d);
            chunks.add(RagContextChunkResponse.builder()
                    .source("ocr")
                    .score(score)
                    .excerpt(excerpt)
                    .build());

            if (chunks.size() >= 5) {
                break;
            }
        }
        return chunks;
    }

    private DocumentRagContextResponse buildRagResponse(String documentId,
                                                        Optional<String> version,
                                                        String tenantId,
                                                        String category,
                                                        boolean enabled,
                                                        String status,
                                                        String message,
                                                        List<RagContextChunkResponse> chunks,
                                                        Instant startedAt) {
        int chunkCount = chunks.size();
        double averageScore = chunks.isEmpty()
                ? 0.0d
                : chunks.stream().mapToDouble(RagContextChunkResponse::getScore).average().orElse(0.0d);
        long latencyMs = Math.max(0L, Duration.between(startedAt, Instant.now()).toMillis());
        String qualityBand = resolveRagQualityBand(status, chunkCount, averageScore);

        incrementRagCounter(tenantId, status, category, chunkCount, averageScore, qualityBand);
        Timer.builder("dms.ai.document.rag.latency")
                .description("RAG context latency by tenant/status/category")
                .tag("tenant", sanitizeTenantTag(tenantId))
                .tag("status", sanitizeMetricTag(status, "unknown"))
                .tag("category", sanitizeMetricTag(category, "unknown"))
                .register(meterRegistry)
                .record(Duration.ofMillis(latencyMs));

        return DocumentRagContextResponse.builder()
                .documentId(documentId)
                .version(version.orElse(null))
                .enabled(enabled)
                .status(status)
                .message(message)
                .category(StringUtils.defaultIfBlank(category, "unknown"))
                .chunkCount(chunkCount)
                .averageScore(averageScore)
                .latencyMs(latencyMs)
                .qualityBand(qualityBand)
                .chunks(chunks)
                .build();
    }

    private void incrementRagCounter(String tenantId, String status, String category, int chunkCount, double averageScore, String qualityBand) {
        Counter.builder("dms.ai.document.rag.requests")
                .description("RAG context requests by tenant/status/category/chunk volume")
                .tag("tenant", sanitizeTenantTag(tenantId))
                .tag("status", sanitizeMetricTag(status, "unknown"))
                .tag("category", sanitizeMetricTag(category, "unknown"))
                .tag("chunk_bucket", resolveChunkBucket(chunkCount))
                .tag("score_bucket", resolveScoreBucket(averageScore))
                .tag("quality_band", sanitizeMetricTag(qualityBand, "unknown"))
                .register(meterRegistry)
                .increment();
    }

    private String resolveChunkBucket(int chunkCount) {
        if (chunkCount <= 0) {
            return "0";
        }
        if (chunkCount <= 2) {
            return "1-2";
        }
        if (chunkCount <= 5) {
            return "3-5";
        }
        return "6+";
    }

    private String resolveScoreBucket(double averageScore) {
        if (averageScore <= 0.0d) {
            return "0";
        }
        if (averageScore < 0.50d) {
            return "0-0.49";
        }
        if (averageScore < 0.75d) {
            return "0.50-0.74";
        }
        return "0.75+";
    }

    private String resolveRagQualityBand(String status, int chunkCount, double averageScore) {
        if (!"READY".equalsIgnoreCase(StringUtils.defaultString(status))) {
            return "BLOCKED";
        }
        if (chunkCount <= 0 || averageScore < 0.35d) {
            return "LOW";
        }
        if (averageScore < 0.70d || chunkCount <= 2) {
            return "MEDIUM";
        }
        return "HIGH";
    }

    private String sanitizeTenantTag(String tenantId) {
        return sanitizeMetricTag(tenantId, "unknown");
    }

    private String sanitizeMetricTag(String value, String fallback) {
        String normalized = StringUtils.lowerCase(StringUtils.trimToEmpty(value));
        if (StringUtils.isBlank(normalized)) {
            return fallback;
        }
        return normalized.replaceAll("[^a-z0-9._-]", "_");
    }

    private Set<String> parseEnabledTenants(String raw) {
        return StringUtils.isBlank(raw)
                ? Set.of()
                : List.of(raw.split(",")).stream()
                .map(StringUtils::trim)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
    }

    private Set<String> parseEnabledCategories(String raw) {
        return StringUtils.isBlank(raw)
                ? Set.of()
                : List.of(raw.split(",")).stream()
                .map(StringUtils::trim)
                .filter(StringUtils::isNotBlank)
                .map(StringUtils::lowerCase)
                .collect(Collectors.toSet());
    }
}
