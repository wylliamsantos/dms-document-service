package br.com.dms.service;

import br.com.dms.controller.request.DocumentChatRequest;
import br.com.dms.controller.response.DocumentChatResponse;
import br.com.dms.controller.response.DocumentInsightResponse;
import br.com.dms.controller.response.DocumentRagContextResponse;
import br.com.dms.domain.mongodb.DmsDocument;
import br.com.dms.domain.mongodb.DmsDocumentVersion;
import br.com.dms.exception.DmsDocumentNotFoundException;
import br.com.dms.exception.TypeException;
import br.com.dms.repository.mongo.DmsDocumentRepository;
import br.com.dms.repository.mongo.DmsDocumentVersionRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class DocumentChatService {

    private static final Logger log = LoggerFactory.getLogger(DocumentChatService.class);
    private static final int MAX_CONTEXT_CHUNKS = 4;
    private static final int MAX_CHUNK_SIZE = 450;

    private final TenantContextService tenantContextService;
    private final DmsDocumentRepository dmsDocumentRepository;
    private final DmsDocumentVersionRepository dmsDocumentVersionRepository;
    private final DocumentInsightService documentInsightService;
    private final MeterRegistry meterRegistry;
    private final RestTemplate restTemplate;
    private final boolean ragEnabled;
    private final boolean chatEnabled;
    private final boolean localProviderEnabled;
    private final String localProviderBaseUrl;
    private final String localProviderModel;

    public DocumentChatService(TenantContextService tenantContextService,
                               DmsDocumentRepository dmsDocumentRepository,
                               DmsDocumentVersionRepository dmsDocumentVersionRepository,
                               DocumentInsightService documentInsightService,
                               MeterRegistry meterRegistry,
                               @Value("${dms.ai.rag.document.enabled:false}") boolean ragEnabled,
                               @Value("${dms.ai.chat.document.enabled:false}") boolean chatEnabled,
                               @Value("${dms.ai.provider.local.enabled:true}") boolean localProviderEnabled,
                               @Value("${dms.ai.provider.local.base-url:http://localhost:11434}") String localProviderBaseUrl,
                               @Value("${dms.ai.provider.local.model:llama3.2:1b}") String localProviderModel,
                               @Value("${dms.ai.provider.local.connect-timeout-ms:5000}") int connectTimeoutMs,
                               @Value("${dms.ai.provider.local.read-timeout-ms:120000}") int readTimeoutMs) {
        this.tenantContextService = tenantContextService;
        this.dmsDocumentRepository = dmsDocumentRepository;
        this.dmsDocumentVersionRepository = dmsDocumentVersionRepository;
        this.documentInsightService = documentInsightService;
        this.meterRegistry = meterRegistry;
        this.ragEnabled = ragEnabled;
        this.chatEnabled = chatEnabled;
        this.localProviderEnabled = localProviderEnabled;
        this.localProviderBaseUrl = localProviderBaseUrl;
        this.localProviderModel = localProviderModel;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    public DocumentChatResponse chat(String documentId, DocumentChatRequest request) {
        long startedAt = System.nanoTime();

        if (!ragEnabled || !chatEnabled) {
            return buildResponseWithMetrics(
                    "unknown",
                    "DISABLED",
                    null,
                    startedAt,
                    DocumentChatResponse.builder()
                            .documentId(documentId)
                            .version(request.getVersion())
                            .enabled(false)
                            .status("DISABLED")
                            .message("Chat/RAG desabilitado por feature flags (dms.ai.rag.document.enabled e/ou dms.ai.chat.document.enabled).")
                            .rolloutGuard("FEATURE_FLAG_DISABLED")
                            .contextChunks(List.of())
            );
        }

        String tenantId = tenantContextService.requireTenantId();
        DmsDocument document = dmsDocumentRepository.findByIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> new DmsDocumentNotFoundException("Document not found", TypeException.VALID));

        Optional<DmsDocumentVersion> targetVersion = StringUtils.isNotBlank(request.getVersion())
                ? dmsDocumentVersionRepository.findByTenantIdAndDmsDocumentIdAndVersionNumber(tenantId, documentId, request.getVersion())
                : dmsDocumentVersionRepository.findLastVersionByTenantIdAndDmsDocumentId(tenantId, documentId);

        String resolvedVersion = targetVersion
                .map(v -> v.getVersionNumber().stripTrailingZeros().toPlainString())
                .orElse(request.getVersion());

        Optional<String> resolvedVersionOptional = Optional.ofNullable(StringUtils.trimToNull(resolvedVersion));
        DocumentInsightResponse insight = resolveInsight(documentId, resolvedVersionOptional);
        DocumentRagContextResponse ragContext = documentInsightService.getRagContextSkeleton(documentId, resolvedVersionOptional);

        if (!"READY".equalsIgnoreCase(ragContext.getStatus())) {
            return buildResponseWithMetrics(
                    tenantId,
                    ragContext.getStatus(),
                    null,
                    startedAt,
                    enrichWithInsight(
                            DocumentChatResponse.builder()
                                    .documentId(documentId)
                                    .version(resolvedVersion)
                                    .enabled(false)
                                    .status(ragContext.getStatus())
                                    .message(ragContext.getMessage())
                                    .rolloutGuard(ragContext.getRolloutGuard())
                                    .contextChunks(List.of()),
                            insight
                    )
            );
        }

        List<String> contextChunks = buildContextChunks(document);
        String prompt = buildPrompt(document, contextChunks, request.getMessage());

        if (!localProviderEnabled) {
            return buildResponseWithMetrics(
                    tenantId,
                    "PROVIDER_DISABLED",
                    null,
                    startedAt,
                    enrichWithInsight(
                            DocumentChatResponse.builder()
                                    .documentId(documentId)
                                    .version(resolvedVersion)
                                    .enabled(true)
                                    .status("PROVIDER_DISABLED")
                                    .message("Provedor local de IA desabilitado (dms.ai.provider.local.enabled=false).")
                                    .rolloutGuard("NONE")
                                    .contextChunks(contextChunks),
                            insight
                    )
            );
        }

        try {
            ChatResult result = queryLocalProvider(prompt);
            return buildResponseWithMetrics(
                    tenantId,
                    "OK",
                    result.model(),
                    startedAt,
                    enrichWithInsight(
                            DocumentChatResponse.builder()
                                    .documentId(documentId)
                                    .version(resolvedVersion)
                                    .enabled(true)
                                    .status("OK")
                                    .message("Resposta gerada com contexto do documento.")
                                    .rolloutGuard("NONE")
                                    .answer(result.answer())
                                    .model(result.model())
                                    .contextChunks(contextChunks),
                            insight
                    )
            );
        } catch (Exception ex) {
            log.warn("Falha ao consultar provedor local de IA (baseUrl={}, model={}): {}", localProviderBaseUrl, localProviderModel, ex.getMessage());
            return buildResponseWithMetrics(
                    tenantId,
                    "PROVIDER_UNAVAILABLE",
                    localProviderModel,
                    startedAt,
                    enrichWithInsight(
                            DocumentChatResponse.builder()
                                    .documentId(documentId)
                                    .version(resolvedVersion)
                                    .enabled(true)
                                    .status("PROVIDER_UNAVAILABLE")
                                    .message("Serviço local de IA indisponível. Clique em 'Tentar novamente'.")
                                    .rolloutGuard("NONE")
                                    .contextChunks(contextChunks),
                            insight
                    )
            );
        }
    }

    private ChatResult queryLocalProvider(String prompt) {
        List<String> candidates = resolveModelCandidates();
        log.info("Chat IA local - baseUrl={} modeloConfigurado={} candidatos={}", localProviderBaseUrl, localProviderModel, candidates);

        Exception lastError = null;
        for (String candidate : candidates) {
            try {
                String answer = queryLocalProviderWithModel(prompt, candidate);
                return new ChatResult(candidate, answer);
            } catch (Exception ex) {
                lastError = ex;
                log.warn("Falha no modelo '{}' (baseUrl={}): {}", candidate, localProviderBaseUrl, ex.getMessage());
            }
        }

        throw new RuntimeException("Todos os modelos candidatos falharam", lastError);
    }

    private String queryLocalProviderWithModel(String prompt, String model) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("prompt", prompt);
        payload.put("stream", false);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                localProviderBaseUrl + "/api/generate",
                new HttpEntity<>(payload, headers),
                Map.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Erro ao consultar provedor local");
        }

        Object providerError = response.getBody().get("error");
        if (providerError != null) {
            throw new RuntimeException("Erro do provedor local: " + providerError);
        }

        Object answer = response.getBody().get("response");
        if (answer == null || StringUtils.isBlank(answer.toString())) {
            throw new RuntimeException("Resposta vazia do provedor local");
        }
        return answer.toString();
    }

    private List<String> resolveModelCandidates() {
        List<String> candidates = new ArrayList<>();
        if (StringUtils.isNotBlank(localProviderModel)) {
            candidates.add(localProviderModel.trim());
        }

        try {
            ResponseEntity<Map> tags = restTemplate.getForEntity(localProviderBaseUrl + "/api/tags", Map.class);
            if (tags.getStatusCode().is2xxSuccessful() && tags.getBody() != null) {
                Object modelsObj = tags.getBody().get("models");
                if (modelsObj instanceof List<?> models) {
                    for (Object item : models) {
                        if (item instanceof Map<?, ?> modelMap) {
                            Object name = modelMap.get("name");
                            if (name != null) {
                                String modelName = String.valueOf(name).trim();
                                if (StringUtils.isNotBlank(modelName) && !candidates.contains(modelName)) {
                                    candidates.add(modelName);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Não foi possível consultar /api/tags em {}: {}", localProviderBaseUrl, ex.getMessage());
        }

        return candidates;
    }

    private List<String> buildContextChunks(DmsDocument document) {
        List<String> chunks = new ArrayList<>();

        if (document.getMetadata() != null && !document.getMetadata().isEmpty()) {
            String metadataChunk = document.getMetadata().entrySet().stream()
                    .limit(12)
                    .map(entry -> entry.getKey() + ": " + String.valueOf(entry.getValue()))
                    .collect(Collectors.joining("\n"));
            if (StringUtils.isNotBlank(metadataChunk)) {
                chunks.add("Metadados relevantes:\n" + metadataChunk);
            }
        }

        String ocrText = StringUtils.trimToEmpty(document.getOcrText());
        if (StringUtils.isBlank(ocrText)) {
            return chunks.stream().limit(MAX_CONTEXT_CHUNKS).toList();
        }

        String normalized = ocrText.replace("\r", "\n");
        String[] paragraphs = normalized.split("\\n\\s*\\n");
        for (String paragraph : paragraphs) {
            String clean = StringUtils.normalizeSpace(paragraph);
            if (StringUtils.isBlank(clean)) {
                continue;
            }
            int end = Math.min(clean.length(), MAX_CHUNK_SIZE);
            chunks.add(clean.substring(0, end));
            if (chunks.size() >= MAX_CONTEXT_CHUNKS) {
                break;
            }
        }

        return chunks;
    }

    private DocumentChatResponse.DocumentChatResponseBuilder enrichWithInsight(DocumentChatResponse.DocumentChatResponseBuilder builder,
                                                                                DocumentInsightResponse insight) {
        if (insight == null) {
            return builder;
        }
        return builder
                .ocrQualityScore(insight.getOcrQualityScore())
                .ocrQualityBand(insight.getOcrQualityBand())
                .ocrQualitySummary(insight.getOcrQualitySummary())
                .missingRequiredMetadata(Optional.ofNullable(insight.getMissingRequiredMetadata()).orElse(List.of()))
                .metadataActionHints(Optional.ofNullable(insight.getMetadataActionHints()).orElse(List.of()));
    }

    private DocumentInsightResponse resolveInsight(String documentId, Optional<String> version) {
        try {
            return documentInsightService.getInsight(documentId, version);
        } catch (Exception ex) {
            log.warn("Falha ao resolver insight para chat do documento {}: {}", documentId, ex.getMessage());
            return null;
        }
    }

    private DocumentChatResponse buildResponseWithMetrics(String tenantId,
                                                          String status,
                                                          String model,
                                                          long startedAt,
                                                          DocumentChatResponse.DocumentChatResponseBuilder builder) {
        long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        incrementChatRequestCounter(tenantId, status, model);
        recordChatLatency(tenantId, status, latencyMs);
        if (!"OK".equals(status)) {
            incrementChatErrorCounter(tenantId, status);
        }
        return builder.latencyMs(latencyMs).build();
    }

    private void incrementChatRequestCounter(String tenantId, String status, String model) {
        Counter.builder("dms.ai.document.chat.requests")
                .description("Chat requests by tenant/status/model")
                .tag("tenant", sanitizeMetricTag(tenantId, "unknown"))
                .tag("status", sanitizeMetricTag(status, "unknown"))
                .tag("model", sanitizeMetricTag(model, "unknown"))
                .register(meterRegistry)
                .increment();
    }

    private void incrementChatErrorCounter(String tenantId, String status) {
        Counter.builder("dms.ai.document.chat.errors")
                .description("Chat errors by tenant/status")
                .tag("tenant", sanitizeMetricTag(tenantId, "unknown"))
                .tag("status", sanitizeMetricTag(status, "unknown"))
                .register(meterRegistry)
                .increment();
    }

    private void recordChatLatency(String tenantId, String status, long latencyMs) {
        Timer.builder("dms.ai.document.chat.latency")
                .description("Chat latency by tenant/status")
                .tag("tenant", sanitizeMetricTag(tenantId, "unknown"))
                .tag("status", sanitizeMetricTag(status, "unknown"))
                .register(meterRegistry)
                .record(latencyMs, TimeUnit.MILLISECONDS);
    }

    private String sanitizeMetricTag(String value, String fallback) {
        String normalized = StringUtils.lowerCase(StringUtils.trimToEmpty(value));
        if (StringUtils.isBlank(normalized)) {
            return fallback;
        }
        return normalized.replaceAll("[^a-z0-9._-]", "_");
    }

    private record ChatResult(String model, String answer) {}

    private String buildPrompt(DmsDocument document, List<String> contextChunks, String question) {
        StringBuilder builder = new StringBuilder();
        builder.append("Você é um assistente do DMS. Responda em português do Brasil, objetivo e fiel ao contexto.\n");
        builder.append("Documento: ").append(StringUtils.defaultString(document.getFilename(), document.getId())).append("\n");
        builder.append("Categoria: ").append(StringUtils.defaultString(document.getCategory(), "n/a")).append("\n\n");
        builder.append("Contexto recuperado:\n");

        for (int i = 0; i < contextChunks.size(); i++) {
            builder.append("[").append(i + 1).append("] ").append(contextChunks.get(i)).append("\n");
        }

        if (contextChunks.isEmpty()) {
            builder.append("(Sem contexto OCR disponível; use apenas metadados básicos)\n");
        }

        builder.append("\nPergunta do usuário: ").append(question).append("\n");
        builder.append("Se não houver informação suficiente, diga explicitamente que não encontrou no documento.");
        return builder.toString();
    }
}
