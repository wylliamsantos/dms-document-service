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
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DocumentInsightService {

    private final AiMetadataSuggestionService aiMetadataSuggestionService;
    private final TenantContextService tenantContextService;
    private final DmsDocumentRepository dmsDocumentRepository;
    private final MeterRegistry meterRegistry;
    private final boolean ragEnabled;
    private final Set<String> ragEnabledTenants;

    public DocumentInsightService(AiMetadataSuggestionService aiMetadataSuggestionService,
                                  TenantContextService tenantContextService,
                                  DmsDocumentRepository dmsDocumentRepository,
                                  MeterRegistry meterRegistry,
                                  @Value("${dms.ai.rag.document.enabled:false}") boolean ragEnabled,
                                  @Value("${dms.ai.rag.document.enabled-tenants:}") String ragEnabledTenants) {
        this.aiMetadataSuggestionService = aiMetadataSuggestionService;
        this.tenantContextService = tenantContextService;
        this.dmsDocumentRepository = dmsDocumentRepository;
        this.meterRegistry = meterRegistry;
        this.ragEnabled = ragEnabled;
        this.ragEnabledTenants = parseEnabledTenants(ragEnabledTenants);
    }

    public DocumentInsightResponse getInsight(String documentId, Optional<String> version) {
        String tenantId = tenantContextService.requireTenantId();
        MetadataSuggestionResponse suggestion = aiMetadataSuggestionService.suggest(documentId, version);
        DmsDocument document = resolveDocument(documentId, tenantId);

        Map<String, Object> persistedMetadataPreview = extractMetadataPreview(document);
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

        if (!ragEnabled) {
            incrementRagCounter(tenantId, "DISABLED", 0);
            return DocumentRagContextResponse.builder()
                    .documentId(documentId)
                    .version(version.orElse(null))
                    .enabled(false)
                    .status("DISABLED")
                    .message("RAG de documento desabilitado por feature flag (dms.ai.rag.document.enabled=false).")
                    .chunks(List.of())
                    .build();
        }

        if (!ragEnabledTenants.isEmpty() && !ragEnabledTenants.contains(tenantId)) {
            incrementRagCounter(tenantId, "TENANT_DISABLED", 0);
            return DocumentRagContextResponse.builder()
                    .documentId(documentId)
                    .version(version.orElse(null))
                    .enabled(false)
                    .status("TENANT_DISABLED")
                    .message("RAG de documento desabilitado para o tenant atual (allowlist não inclui este tenant).")
                    .chunks(List.of())
                    .build();
        }

        DmsDocument document = dmsDocumentRepository.findByIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> new DmsDocumentNotFoundException("Document not found", TypeException.VALID));

        List<RagContextChunkResponse> chunks = buildChunks(document);
        incrementRagCounter(tenantId, "READY", chunks.size());
        return DocumentRagContextResponse.builder()
                .documentId(documentId)
                .version(version.orElse(null))
                .enabled(true)
                .status("READY")
                .message(chunks.isEmpty() ? "Sem chunks de OCR disponíveis para este documento." : "Contexto RAG local carregado.")
                .chunks(chunks)
                .build();
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

    private void incrementRagCounter(String tenantId, String status, int chunkCount) {
        Counter.builder("dms.ai.document.rag.requests")
                .description("RAG context requests by tenant/status/chunk volume")
                .tag("tenant", sanitizeTenantTag(tenantId))
                .tag("status", sanitizeMetricTag(status, "unknown"))
                .tag("chunk_bucket", resolveChunkBucket(chunkCount))
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
}
