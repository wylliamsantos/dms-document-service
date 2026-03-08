package br.com.dms.service;

import br.com.dms.controller.response.DocumentInsightResponse;
import br.com.dms.controller.response.DocumentRagContextResponse;
import br.com.dms.controller.response.InsightSignalResponse;
import br.com.dms.controller.response.MetadataActionHintResponse;
import br.com.dms.controller.response.MetadataSuggestionResponse;
import br.com.dms.controller.response.MetadataUpdateHistoryBucketResponse;
import br.com.dms.controller.response.MetadataUpdateHistoryCategorySummaryResponse;
import br.com.dms.controller.response.MetadataUpdateHistoryTenantCategoryBucketResponse;
import br.com.dms.controller.response.MetadataUpdateHistoryTenantCategorySummaryResponse;
import br.com.dms.controller.response.MetadataRegressionAlertResponse;
import br.com.dms.controller.response.MetadataUpdateAdoptionTrendPointResponse;
import br.com.dms.controller.response.MetadataUpdateOcrHintAdoptionResponse;
import br.com.dms.controller.response.MetadataUpdateHistoryEntryResponse;
import br.com.dms.controller.response.MetadataUpdateHistoryPageResponse;
import br.com.dms.controller.response.MetadataUpdateHistorySummaryResponse;
import br.com.dms.controller.response.RagContextChunkResponse;
import br.com.dms.domain.mongodb.Category;
import br.com.dms.domain.mongodb.DmsDocument;
import br.com.dms.domain.mongodb.MetadataUpdateHistoryEntry;
import br.com.dms.exception.DmsDocumentNotFoundException;
import br.com.dms.exception.TypeException;
import br.com.dms.repository.mongo.CategoryRepository;
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
    private final CategoryRepository categoryRepository;
    private final MeterRegistry meterRegistry;
    private final boolean ragEnabled;
    private final Set<String> ragEnabledTenants;
    private final Set<String> ragEnabledCategories;

    public DocumentInsightService(AiMetadataSuggestionService aiMetadataSuggestionService,
                                  TenantContextService tenantContextService,
                                  DmsDocumentRepository dmsDocumentRepository,
                                  CategoryRepository categoryRepository,
                                  MeterRegistry meterRegistry,
                                  @Value("${dms.ai.rag.document.enabled:false}") boolean ragEnabled,
                                  @Value("${dms.ai.rag.document.enabled-tenants:}") String ragEnabledTenants,
                                  @Value("${dms.ai.rag.document.enabled-categories:}") String ragEnabledCategories) {
        this.aiMetadataSuggestionService = aiMetadataSuggestionService;
        this.tenantContextService = tenantContextService;
        this.dmsDocumentRepository = dmsDocumentRepository;
        this.categoryRepository = categoryRepository;
        this.meterRegistry = meterRegistry;
        this.ragEnabled = ragEnabled;
        this.ragEnabledTenants = parseEnabledTenants(ragEnabledTenants);
        this.ragEnabledCategories = parseEnabledCategories(ragEnabledCategories);
    }

    public DocumentInsightResponse getInsight(String documentId, Optional<String> version) {
        return getInsight(documentId, version, Optional.empty());
    }

    public DocumentInsightResponse getInsight(String documentId, Optional<String> version, Optional<Integer> ocrHintLookbackDays) {
        String tenantId = tenantContextService.requireTenantId();
        MetadataSuggestionResponse suggestion = aiMetadataSuggestionService.suggest(documentId, version);
        DmsDocument document = resolveDocument(documentId, tenantId);

        Map<String, Object> persistedMetadataPreview = extractMetadataPreview(document);
        Map<String, Object> importantPersistedMetadata = extractImportantPersistedMetadata(document);
        String importantPersistedMetadataSummary = summarizeImportantPersistedMetadata(importantPersistedMetadata);
        int persistedMetadataCount = countPersistedMetadata(document);
        boolean hasPersistedOcrText = StringUtils.isNotBlank(document == null ? null : document.getOcrText());
        String persistedOcrExcerpt = resolvePersistedOcrExcerpt(document);
        List<String> expectedRequiredMetadata = resolveExpectedRequiredMetadata(tenantId, document);
        List<String> missingRequiredMetadata = resolveMissingRequiredMetadata(document, expectedRequiredMetadata);
        int requiredMetadataCoveragePercent = resolveRequiredMetadataCoveragePercent(expectedRequiredMetadata, missingRequiredMetadata);
        List<MetadataActionHintResponse> metadataActionHints = resolveMetadataActionHints(document, missingRequiredMetadata);
        List<MetadataUpdateHistoryEntryResponse> metadataUpdateHistory = resolveMetadataUpdateHistory(document);
        List<MetadataRegressionAlertResponse> metadataRegressionAlerts = resolveMetadataRegressionAlerts(tenantId, document);
        MetadataUpdateOcrHintAdoptionResponse ocrHintAdoption = resolveOcrHintAdoption(tenantId, document, ocrHintLookbackDays);
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
                .importantPersistedMetadataSummary(importantPersistedMetadataSummary)
                .persistedMetadataCount(persistedMetadataCount)
                .hasPersistedOcrText(hasPersistedOcrText)
                .persistedOcrExcerpt(persistedOcrExcerpt)
                .expectedRequiredMetadata(expectedRequiredMetadata)
                .missingRequiredMetadata(missingRequiredMetadata)
                .requiredMetadataCoveragePercent(requiredMetadataCoveragePercent)
                .metadataActionHints(metadataActionHints)
                .metadataUpdateHistory(metadataUpdateHistory)
                .metadataRegressionAlerts(metadataRegressionAlerts)
                .ocrHintAdoption(ocrHintAdoption)
                .ocrStats(resolveOcrStats(document))
                .build();
    }

    public MetadataUpdateHistoryPageResponse getMetadataUpdateHistory(String documentId,
                                                                      Optional<String> version,
                                                                      int page,
                                                                      int size,
                                                                      Optional<String> source,
                                                                      Optional<String> field,
                                                                      Optional<Instant> updatedFrom,
                                                                      Optional<Instant> updatedTo) {
        String tenantId = tenantContextService.requireTenantId();
        DmsDocument document = resolveDocument(documentId, tenantId);

        if (document == null) {
            throw new DmsDocumentNotFoundException("Document not found", TypeException.VALID);
        }

        List<MetadataUpdateHistoryEntryResponse> ordered = filterMetadataUpdateHistory(document, source, field, updatedFrom, updatedTo, Optional.empty());
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        int fromIndex = Math.min(safePage * safeSize, ordered.size());
        int toIndex = Math.min(fromIndex + safeSize, ordered.size());

        return MetadataUpdateHistoryPageResponse.builder()
                .content(ordered.subList(fromIndex, toIndex))
                .totalElements(ordered.size())
                .number(safePage)
                .size(safeSize)
                .build();
    }

    public MetadataUpdateHistorySummaryResponse getMetadataUpdateHistorySummary(String documentId,
                                                                                Optional<String> version,
                                                                                Optional<String> source,
                                                                                Optional<String> field,
                                                                                Optional<Instant> updatedFrom,
                                                                                Optional<Instant> updatedTo,
                                                                                Optional<String> ocrHintAction) {
        String tenantId = tenantContextService.requireTenantId();
        DmsDocument document = resolveDocument(documentId, tenantId);

        if (document == null) {
            throw new DmsDocumentNotFoundException("Document not found", TypeException.VALID);
        }

        List<MetadataUpdateHistoryEntryResponse> allEntries = toMetadataUpdateHistory(document);
        List<MetadataUpdateHistoryEntryResponse> filteredEntries = filterMetadataUpdateHistory(document, source, field, updatedFrom, updatedTo, ocrHintAction);

        long ocrHintAppliedEntries = countBySource(filteredEntries, "OCR_HINT");
        long ocrHintCancelledEntries = countBySources(filteredEntries, List.of("OCR_HINT_CANCEL", "OCR_HINT_DISMISSED"));
        long ocrHintErrorEntries = countBySource(filteredEntries, "OCR_HINT_ERROR");

        return MetadataUpdateHistorySummaryResponse.builder()
                .totalEntries(allEntries.size())
                .filteredEntries(filteredEntries.size())
                .latestUpdatedAt(filteredEntries.stream().map(MetadataUpdateHistoryEntryResponse::getUpdatedAt).findFirst().orElse(null))
                .bySource(buildHistoryBuckets(filteredEntries, MetadataUpdateHistoryEntryResponse::getSource))
                .byField(buildHistoryBuckets(filteredEntries, MetadataUpdateHistoryEntryResponse::getField))
                .ocrHintAppliedEntries(ocrHintAppliedEntries)
                .ocrHintCancelledEntries(ocrHintCancelledEntries)
                .ocrHintErrorEntries(ocrHintErrorEntries)
                .ocrHintAppliedRate(resolveRatio(ocrHintAppliedEntries, filteredEntries.size()))
                .build();
    }

    public MetadataUpdateHistoryCategorySummaryResponse getMetadataUpdateHistoryCategorySummary(String documentId,
                                                                                                Optional<String> version,
                                                                                                Optional<String> category,
                                                                                                Optional<String> source,
                                                                                                Optional<String> field,
                                                                                                Optional<Instant> updatedFrom,
                                                                                                Optional<Instant> updatedTo,
                                                                                                Optional<String> ocrHintAction) {
        String tenantId = tenantContextService.requireTenantId();
        DmsDocument referenceDocument = resolveDocument(documentId, tenantId);

        if (referenceDocument == null) {
            throw new DmsDocumentNotFoundException("Document not found", TypeException.VALID);
        }

        String referenceCategory = StringUtils.trimToEmpty(referenceDocument.getCategory());
        String requestedCategory = category.map(StringUtils::trimToEmpty).orElse("");
        String effectiveCategory = StringUtils.defaultIfBlank(requestedCategory, referenceCategory);
        boolean categoryOverride = StringUtils.isNotBlank(requestedCategory) && !StringUtils.equalsIgnoreCase(requestedCategory, referenceCategory);

        Counter.builder("dms.ai.document.ocr_hint.benchmark.category.requests")
                .description("Benchmark OCR_HINT category summary requests (default vs override)")
                .tag("tenant", sanitizeTenantTag(tenantId))
                .tag("mode", categoryOverride ? "drilldown_override" : "default")
                .tag("ocr_hint_action", sanitizeMetricTag(ocrHintAction.orElse("ALL"), "all"))
                .register(meterRegistry)
                .increment();

        List<DmsDocument> categoryDocuments = StringUtils.isBlank(effectiveCategory)
                ? List.of(referenceDocument)
                : dmsDocumentRepository.findByTenantIdAndCategory(tenantId, effectiveCategory);

        MetadataUpdateHistoryTenantCategoryBucketResponse bucket = buildTenantCategoryBucket(effectiveCategory, categoryDocuments, source, field, updatedFrom, updatedTo, ocrHintAction);

        return MetadataUpdateHistoryCategorySummaryResponse.builder()
                .category(bucket.getCategory())
                .totalDocumentsInCategory(bucket.getTotalDocumentsInCategory())
                .totalDocumentsWithUpdates(bucket.getTotalDocumentsWithUpdates())
                .totalEntries(bucket.getTotalEntries())
                .filteredEntries(bucket.getFilteredEntries())
                .latestUpdatedAt(bucket.getLatestUpdatedAt())
                .bySource(bucket.getBySource())
                .byField(bucket.getByField())
                .ocrHintAppliedEntries(bucket.getOcrHintAppliedEntries())
                .ocrHintCancelledEntries(bucket.getOcrHintCancelledEntries())
                .ocrHintErrorEntries(bucket.getOcrHintErrorEntries())
                .ocrHintAppliedRate(bucket.getOcrHintAppliedRate())
                .build();
    }

    public MetadataUpdateHistoryTenantCategorySummaryResponse getMetadataUpdateHistoryTenantCategorySummary(Optional<String> category,
                                                                                                             Optional<String> source,
                                                                                                             Optional<String> field,
                                                                                                             Optional<Instant> updatedFrom,
                                                                                                             Optional<Instant> updatedTo,
                                                                                                             Optional<String> ocrHintAction,
                                                                                                             int limit) {
        String tenantId = tenantContextService.requireTenantId();
        int safeLimit = Math.max(1, Math.min(limit, 20));

        List<DmsDocument> tenantDocuments = dmsDocumentRepository.findByTenantId(tenantId);
        Map<String, List<DmsDocument>> docsByCategory = tenantDocuments.stream()
                .collect(Collectors.groupingBy(doc -> StringUtils.defaultIfBlank(StringUtils.trimToEmpty(doc.getCategory()), "SEM_CATEGORIA")));

        List<MetadataUpdateHistoryTenantCategoryBucketResponse> buckets = docsByCategory.entrySet().stream()
                .filter(entry -> category == null || category.isEmpty() || StringUtils.isBlank(category.get())
                        || StringUtils.equalsIgnoreCase(StringUtils.trimToEmpty(category.get()), entry.getKey()))
                .map(entry -> buildTenantCategoryBucket(entry.getKey(), entry.getValue(), source, field, updatedFrom, updatedTo, ocrHintAction))
                .filter(bucket -> bucket.getFilteredEntries() > 0)
                .sorted((left, right) -> Integer.compare(right.getFilteredEntries(), left.getFilteredEntries()))
                .limit(safeLimit)
                .toList();

        int totalEntries = buckets.stream().mapToInt(MetadataUpdateHistoryTenantCategoryBucketResponse::getTotalEntries).sum();
        int filteredEntries = buckets.stream().mapToInt(MetadataUpdateHistoryTenantCategoryBucketResponse::getFilteredEntries).sum();

        String latestUpdatedAt = buckets.stream()
                .map(MetadataUpdateHistoryTenantCategoryBucketResponse::getLatestUpdatedAt)
                .filter(StringUtils::isNotBlank)
                .sorted((left, right) -> StringUtils.defaultString(right).compareTo(StringUtils.defaultString(left)))
                .findFirst()
                .orElse(null);

        return MetadataUpdateHistoryTenantCategorySummaryResponse.builder()
                .totalCategories(docsByCategory.size())
                .totalDocuments(tenantDocuments.size())
                .totalEntries(totalEntries)
                .filteredEntries(filteredEntries)
                .latestUpdatedAt(latestUpdatedAt)
                .categories(buckets)
                .build();
    }

    private MetadataUpdateHistoryTenantCategoryBucketResponse buildTenantCategoryBucket(String category,
                                                                                           List<DmsDocument> categoryDocuments,
                                                                                           Optional<String> source,
                                                                                           Optional<String> field,
                                                                                           Optional<Instant> updatedFrom,
                                                                                           Optional<Instant> updatedTo,
                                                                                           Optional<String> ocrHintAction) {
        List<MetadataUpdateHistoryEntryResponse> allEntries = categoryDocuments.stream()
                .flatMap(doc -> toMetadataUpdateHistory(doc).stream())
                .sorted((left, right) -> StringUtils.defaultString(right.getUpdatedAt()).compareTo(StringUtils.defaultString(left.getUpdatedAt())))
                .toList();

        List<MetadataUpdateHistoryEntryResponse> filteredEntries = allEntries.stream()
                .filter(entry -> matchesSource(entry, source))
                .filter(entry -> matchesField(entry, field))
                .filter(entry -> matchesUpdatedFrom(entry, updatedFrom))
                .filter(entry -> matchesUpdatedTo(entry, updatedTo))
                .filter(entry -> matchesOcrHintAction(entry, ocrHintAction))
                .toList();

        int docsWithUpdates = (int) categoryDocuments.stream()
                .filter(doc -> doc.getMetadataUpdateHistory() != null && !doc.getMetadataUpdateHistory().isEmpty())
                .count();

        long ocrHintAppliedEntries = countBySource(filteredEntries, "OCR_HINT");
        long ocrHintCancelledEntries = countBySources(filteredEntries, List.of("OCR_HINT_CANCEL", "OCR_HINT_DISMISSED"));
        long ocrHintErrorEntries = countBySource(filteredEntries, "OCR_HINT_ERROR");

        return MetadataUpdateHistoryTenantCategoryBucketResponse.builder()
                .category(category)
                .totalDocumentsInCategory(categoryDocuments.size())
                .totalDocumentsWithUpdates(docsWithUpdates)
                .totalEntries(allEntries.size())
                .filteredEntries(filteredEntries.size())
                .latestUpdatedAt(filteredEntries.stream().map(MetadataUpdateHistoryEntryResponse::getUpdatedAt).findFirst().orElse(null))
                .bySource(buildHistoryBuckets(filteredEntries, MetadataUpdateHistoryEntryResponse::getSource))
                .byField(buildHistoryBuckets(filteredEntries, MetadataUpdateHistoryEntryResponse::getField))
                .ocrHintAppliedEntries(ocrHintAppliedEntries)
                .ocrHintCancelledEntries(ocrHintCancelledEntries)
                .ocrHintErrorEntries(ocrHintErrorEntries)
                .ocrHintAppliedRate(resolveRatio(ocrHintAppliedEntries, filteredEntries.size()))
                .build();
    }

    private List<MetadataUpdateHistoryEntryResponse> filterMetadataUpdateHistory(DmsDocument document,
                                                                          Optional<String> source,
                                                                          Optional<String> field,
                                                                          Optional<Instant> updatedFrom,
                                                                          Optional<Instant> updatedTo,
                                                                          Optional<String> ocrHintAction) {
        return toMetadataUpdateHistory(document).stream()
                .filter(entry -> matchesSource(entry, source))
                .filter(entry -> matchesField(entry, field))
                .filter(entry -> matchesUpdatedFrom(entry, updatedFrom))
                .filter(entry -> matchesUpdatedTo(entry, updatedTo))
                .filter(entry -> matchesOcrHintAction(entry, ocrHintAction))
                .toList();
    }

    private List<MetadataUpdateHistoryBucketResponse> buildHistoryBuckets(List<MetadataUpdateHistoryEntryResponse> entries,
                                                                           java.util.function.Function<MetadataUpdateHistoryEntryResponse, String> keyResolver) {
        return entries.stream()
                .collect(Collectors.groupingBy(
                        entry -> sanitizeMetricTag(keyResolver.apply(entry), "unknown"),
                        LinkedHashMap::new,
                        Collectors.counting()
                ))
                .entrySet()
                .stream()
                .sorted((left, right) -> Long.compare(right.getValue(), left.getValue()))
                .limit(5)
                .map(bucket -> MetadataUpdateHistoryBucketResponse.builder()
                        .key(bucket.getKey())
                        .count(bucket.getValue())
                        .build())
                .toList();
    }

    private boolean matchesSource(MetadataUpdateHistoryEntryResponse entry, Optional<String> source) {
        if (source == null || source.isEmpty() || StringUtils.isBlank(source.get())) {
            return true;
        }

        return StringUtils.equalsIgnoreCase(
                StringUtils.trimToEmpty(entry.getSource()),
                StringUtils.trimToEmpty(source.get())
        );
    }

    private boolean matchesField(MetadataUpdateHistoryEntryResponse entry, Optional<String> field) {
        if (field == null || field.isEmpty() || StringUtils.isBlank(field.get())) {
            return true;
        }

        return StringUtils.equalsIgnoreCase(
                StringUtils.trimToEmpty(entry.getField()),
                StringUtils.trimToEmpty(field.get())
        );
    }

    private boolean matchesOcrHintAction(MetadataUpdateHistoryEntryResponse entry, Optional<String> ocrHintAction) {
        if (ocrHintAction == null || ocrHintAction.isEmpty() || StringUtils.isBlank(ocrHintAction.get())) {
            return true;
        }

        String normalizedAction = StringUtils.upperCase(StringUtils.trimToEmpty(ocrHintAction.get()));
        String normalizedSource = StringUtils.upperCase(StringUtils.trimToEmpty(entry.getSource()));

        return switch (normalizedAction) {
            case "APPLIED" -> "OCR_HINT".equals(normalizedSource);
            case "CANCEL", "CANCELLED", "CANCELED", "DISMISSED" -> List.of("OCR_HINT_CANCEL", "OCR_HINT_DISMISSED").contains(normalizedSource);
            case "ERROR" -> "OCR_HINT_ERROR".equals(normalizedSource);
            case "ALL" -> true;
            default -> StringUtils.equalsIgnoreCase(normalizedSource, normalizedAction);
        };
    }

    private boolean matchesUpdatedFrom(MetadataUpdateHistoryEntryResponse entry, Optional<Instant> updatedFrom) {
        if (updatedFrom == null || updatedFrom.isEmpty()) {
            return true;
        }

        Optional<Instant> updatedAt = parseInstant(entry.getUpdatedAt());
        return updatedAt.map(instant -> !instant.isBefore(updatedFrom.get())).orElse(false);
    }

    private boolean matchesUpdatedTo(MetadataUpdateHistoryEntryResponse entry, Optional<Instant> updatedTo) {
        if (updatedTo == null || updatedTo.isEmpty()) {
            return true;
        }

        Optional<Instant> updatedAt = parseInstant(entry.getUpdatedAt());
        return updatedAt.map(instant -> !instant.isAfter(updatedTo.get())).orElse(false);
    }

    private Optional<Instant> parseInstant(String value) {
        if (StringUtils.isBlank(value)) {
            return Optional.empty();
        }

        try {
            return Optional.of(Instant.parse(StringUtils.trim(value)));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private List<String> resolveExpectedRequiredMetadata(String tenantId, DmsDocument document) {
        String categoryName = document == null ? "" : StringUtils.trimToEmpty(document.getCategory());
        if (StringUtils.isBlank(categoryName)) {
            return List.of();
        }

        return categoryRepository.findByTenantIdAndName(tenantId, categoryName)
                .map(this::extractRequiredFields)
                .orElseGet(List::of);
    }

    private List<String> extractRequiredFields(Category category) {
        if (category == null || category.getSchema() == null) {
            return List.of();
        }

        Object required = category.getSchema().get("required");
        if (!(required instanceof List<?> fields)) {
            return List.of();
        }

        return fields.stream()
                .map(String::valueOf)
                .map(StringUtils::trimToEmpty)
                .filter(StringUtils::isNotBlank)
                .map(StringUtils::lowerCase)
                .distinct()
                .toList();
    }

    private List<String> resolveMissingRequiredMetadata(DmsDocument document, List<String> expectedRequiredMetadata) {
        if (document == null || document.getMetadata() == null || document.getMetadata().isEmpty() || expectedRequiredMetadata.isEmpty()) {
            return expectedRequiredMetadata;
        }

        Set<String> availableKeys = document.getMetadata().entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .map(Map.Entry::getKey)
                .map(StringUtils::trimToEmpty)
                .map(StringUtils::lowerCase)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());

        return expectedRequiredMetadata.stream()
                .filter(field -> !availableKeys.contains(StringUtils.lowerCase(field)))
                .toList();
    }

    private int resolveRequiredMetadataCoveragePercent(List<String> expectedRequiredMetadata, List<String> missingRequiredMetadata) {
        if (expectedRequiredMetadata == null || expectedRequiredMetadata.isEmpty()) {
            return 100;
        }

        int missing = missingRequiredMetadata == null ? 0 : missingRequiredMetadata.size();
        int covered = Math.max(0, expectedRequiredMetadata.size() - missing);
        return (int) Math.round((covered * 100.0d) / expectedRequiredMetadata.size());
    }

    private List<MetadataActionHintResponse> resolveMetadataActionHints(DmsDocument document, List<String> missingRequiredMetadata) {
        if (missingRequiredMetadata == null || missingRequiredMetadata.isEmpty()) {
            return List.of();
        }

        String persistedOcrText = StringUtils.trimToEmpty(document == null ? null : document.getOcrText());
        boolean hasPersistedOcrText = StringUtils.isNotBlank(persistedOcrText);
        return missingRequiredMetadata.stream()
                .limit(5)
                .map(field -> {
                    OcrFieldSuggestion suggestion = hasPersistedOcrText
                            ? extractFieldValueFromOcr(persistedOcrText, field)
                            : OcrFieldSuggestion.empty();
                    return MetadataActionHintResponse.builder()
                            .field(field)
                            .action(hasPersistedOcrText ? "EXTRACT_FROM_OCR" : "REQUEST_OCR_PROCESSING")
                            .reason(hasPersistedOcrText
                                    ? "OCR já persistido. Priorize extração/validação deste campo e salve no metadado do documento."
                                    : "Sem OCR persistido. Execute a extração OCR antes de preencher este campo obrigatório.")
                            .priority("HIGH")
                            .suggestedValue(suggestion.value())
                            .evidenceExcerpt(suggestion.evidenceExcerpt())
                            .build();
                })
                .toList();
    }

    private OcrFieldSuggestion extractFieldValueFromOcr(String ocrText, String field) {
        if (StringUtils.isBlank(ocrText) || StringUtils.isBlank(field)) {
            return OcrFieldSuggestion.empty();
        }

        String normalizedField = StringUtils.lowerCase(StringUtils.trimToEmpty(field));
        String[] lines = ocrText.replace("\r", "\n").split("\n");
        for (String rawLine : lines) {
            String line = StringUtils.normalizeSpace(rawLine);
            if (StringUtils.isBlank(line)) {
                continue;
            }

            String normalizedLine = StringUtils.lowerCase(line);
            if (!normalizedLine.contains(normalizedField)) {
                continue;
            }

            String candidate = line.replaceFirst("(?i)^.*" + java.util.regex.Pattern.quote(field) + "\\s*[:\\-]?\\s*", "");
            candidate = StringUtils.trimToEmpty(candidate);
            if (StringUtils.isBlank(candidate)) {
                continue;
            }

            if (candidate.length() > 120) {
                candidate = candidate.substring(0, 120);
            }

            String excerpt = line.length() > 180 ? line.substring(0, 180) + "..." : line;
            return new OcrFieldSuggestion(candidate, "Evidência OCR: " + excerpt);
        }

        return OcrFieldSuggestion.empty();
    }

    private record OcrFieldSuggestion(String value, String evidenceExcerpt) {
        private static OcrFieldSuggestion empty() {
            return new OcrFieldSuggestion(null, null);
        }
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

    private int countPersistedMetadata(DmsDocument document) {
        if (document == null || document.getMetadata() == null || document.getMetadata().isEmpty()) {
            return 0;
        }

        return (int) document.getMetadata().values().stream()
                .filter(value -> value instanceof String || value instanceof Number || value instanceof Boolean)
                .count();
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

    private String summarizeImportantPersistedMetadata(Map<String, Object> importantPersistedMetadata) {
        if (importantPersistedMetadata == null || importantPersistedMetadata.isEmpty()) {
            return null;
        }

        return importantPersistedMetadata.entrySet().stream()
                .limit(4)
                .map(entry -> entry.getKey() + ": " + String.valueOf(entry.getValue()))
                .collect(Collectors.joining(" · "));
    }

    private List<MetadataUpdateHistoryEntryResponse> toMetadataUpdateHistory(DmsDocument document) {
        if (document == null || document.getMetadataUpdateHistory() == null || document.getMetadataUpdateHistory().isEmpty()) {
            return List.of();
        }

        return document.getMetadataUpdateHistory().stream()
                .sorted((left, right) -> StringUtils.defaultString(right.getUpdatedAt()).compareTo(StringUtils.defaultString(left.getUpdatedAt())))
                .map(entry -> MetadataUpdateHistoryEntryResponse.builder()
                        .field(entry.getField())
                        .previousValue(entry.getPreviousValue())
                        .newValue(entry.getNewValue())
                        .source(entry.getSource())
                        .updatedAt(entry.getUpdatedAt())
                        .updatedBy(entry.getUpdatedBy())
                        .build())
                .toList();
    }

    private List<MetadataUpdateHistoryEntryResponse> resolveMetadataUpdateHistory(DmsDocument document) {
        return toMetadataUpdateHistory(document).stream()
                .limit(5)
                .toList();
    }

    private List<MetadataRegressionAlertResponse> resolveMetadataRegressionAlerts(String tenantId, DmsDocument document) {
        if (document == null) {
            return List.of();
        }

        List<MetadataUpdateHistoryEntryResponse> documentEntries = toMetadataUpdateHistory(document);
        if (documentEntries.size() < 3) {
            return List.of();
        }

        String category = StringUtils.trimToEmpty(document.getCategory());
        List<DmsDocument> categoryDocuments = StringUtils.isBlank(category)
                ? List.of(document)
                : dmsDocumentRepository.findByTenantIdAndCategory(tenantId, category);

        List<MetadataUpdateHistoryEntryResponse> categoryEntries = categoryDocuments.stream()
                .flatMap(doc -> toMetadataUpdateHistory(doc).stream())
                .toList();
        if (categoryEntries.isEmpty()) {
            return List.of();
        }

        Map<String, Long> documentBySource = buildCountMap(documentEntries, MetadataUpdateHistoryEntryResponse::getSource);
        Map<String, Long> categoryBySource = buildCountMap(categoryEntries, MetadataUpdateHistoryEntryResponse::getSource);
        Map<String, Long> documentByField = buildCountMap(documentEntries, MetadataUpdateHistoryEntryResponse::getField);
        Map<String, Long> categoryByField = buildCountMap(categoryEntries, MetadataUpdateHistoryEntryResponse::getField);

        List<MetadataRegressionAlertResponse> alerts = new ArrayList<>();
        alerts.addAll(buildRegressionAlerts("SOURCE", documentEntries.size(), categoryEntries.size(), documentBySource, categoryBySource));
        alerts.addAll(buildRegressionAlerts("FIELD", documentEntries.size(), categoryEntries.size(), documentByField, categoryByField));

        return alerts.stream()
                .sorted((left, right) -> Double.compare(right.getDeltaRatio(), left.getDeltaRatio()))
                .limit(5)
                .toList();
    }

    private Map<String, Long> buildCountMap(List<MetadataUpdateHistoryEntryResponse> entries,
                                            java.util.function.Function<MetadataUpdateHistoryEntryResponse, String> keyResolver) {
        return entries.stream()
                .collect(Collectors.groupingBy(
                        entry -> sanitizeMetricTag(keyResolver.apply(entry), "unknown"),
                        Collectors.counting()
                ));
    }

    private List<MetadataRegressionAlertResponse> buildRegressionAlerts(String dimension,
                                                                        int documentTotal,
                                                                        int categoryTotal,
                                                                        Map<String, Long> documentCounts,
                                                                        Map<String, Long> categoryCounts) {
        if (documentTotal <= 0 || categoryTotal <= 0 || documentCounts.isEmpty()) {
            return List.of();
        }

        return documentCounts.entrySet().stream()
                .map(entry -> {
                    String key = entry.getKey();
                    long documentCount = entry.getValue();
                    long categoryCount = categoryCounts.getOrDefault(key, 0L);
                    double documentRatio = documentCount / (double) documentTotal;
                    double categoryRatio = categoryCount / (double) categoryTotal;
                    double deltaRatio = documentRatio - categoryRatio;
                    if (documentCount < 2 || deltaRatio < 0.25d) {
                        return null;
                    }

                    String severity = deltaRatio >= 0.45d ? "HIGH" : "MEDIUM";
                    String message = "Volume de ajustes " + key + " acima do benchmark da categoria (" +
                            Math.round(documentRatio * 100) + "% vs " + Math.round(categoryRatio * 100) + "%).";

                    return MetadataRegressionAlertResponse.builder()
                            .dimension(dimension)
                            .key(key)
                            .documentCount(documentCount)
                            .categoryCount(categoryCount)
                            .documentRatio(documentRatio)
                            .categoryRatio(categoryRatio)
                            .deltaRatio(deltaRatio)
                            .severity(severity)
                            .message(message)
                            .build();
                })
                .filter(java.util.Objects::nonNull)
                .sorted((left, right) -> Double.compare(right.getDeltaRatio(), left.getDeltaRatio()))
                .limit(3)
                .toList();
    }

    private MetadataUpdateOcrHintAdoptionResponse resolveOcrHintAdoption(String tenantId,
                                                                           DmsDocument document,
                                                                           Optional<Integer> ocrHintLookbackDays) {
        int lookbackDays = resolveLookbackDays(ocrHintLookbackDays);
        if (document == null) {
            return MetadataUpdateOcrHintAdoptionResponse.builder()
                    .documentTotalUpdates(0)
                     .documentOcrHintUpdates(0)
                    .documentOcrHintCancelUpdates(0)
                    .documentOcrHintErrorUpdates(0)
                    .documentOcrHintRate(0.0d)
                    .categoryTotalUpdates(0)
                     .categoryOcrHintUpdates(0)
                    .categoryOcrHintCancelUpdates(0)
                    .categoryOcrHintErrorUpdates(0)
                    .categoryOcrHintRate(0.0d)
                    .lookbackDaysApplied(lookbackDays)
                    .trend(List.of())
                    .build();
        }

        List<MetadataUpdateHistoryEntryResponse> documentEntries = toMetadataUpdateHistory(document);
        String category = StringUtils.trimToEmpty(document.getCategory());
        List<DmsDocument> categoryDocuments = StringUtils.isBlank(category)
                ? List.of(document)
                : dmsDocumentRepository.findByTenantIdAndCategory(tenantId, category);
        List<MetadataUpdateHistoryEntryResponse> categoryEntries = categoryDocuments.stream()
                .flatMap(doc -> toMetadataUpdateHistory(doc).stream())
                .toList();

        Instant threshold = Instant.now().minus(Duration.ofDays(lookbackDays));
        List<MetadataUpdateHistoryEntryResponse> filteredDocumentEntries = filterByLookback(documentEntries, threshold);
        List<MetadataUpdateHistoryEntryResponse> filteredCategoryEntries = filterByLookback(categoryEntries, threshold);

        long documentTotal = filteredDocumentEntries.size();
        long documentOcrHint = countBySource(filteredDocumentEntries, "OCR_HINT");
        long documentOcrHintCancel = countBySources(filteredDocumentEntries, List.of("OCR_HINT_CANCEL", "OCR_HINT_DISMISSED"));
        long documentOcrHintError = countBySource(filteredDocumentEntries, "OCR_HINT_ERROR");
        long categoryTotal = filteredCategoryEntries.size();
        long categoryOcrHint = countBySource(filteredCategoryEntries, "OCR_HINT");
        long categoryOcrHintCancel = countBySources(filteredCategoryEntries, List.of("OCR_HINT_CANCEL", "OCR_HINT_DISMISSED"));
        long categoryOcrHintError = countBySource(filteredCategoryEntries, "OCR_HINT_ERROR");

        return MetadataUpdateOcrHintAdoptionResponse.builder()
                .documentTotalUpdates(documentTotal)
                 .documentOcrHintUpdates(documentOcrHint)
                .documentOcrHintCancelUpdates(documentOcrHintCancel)
                .documentOcrHintErrorUpdates(documentOcrHintError)
                .documentOcrHintRate(resolveRatio(documentOcrHint, documentTotal))
                .categoryTotalUpdates(categoryTotal)
                 .categoryOcrHintUpdates(categoryOcrHint)
                .categoryOcrHintCancelUpdates(categoryOcrHintCancel)
                .categoryOcrHintErrorUpdates(categoryOcrHintError)
                .categoryOcrHintRate(resolveRatio(categoryOcrHint, categoryTotal))
                .lookbackDaysApplied(lookbackDays)
                .trend(buildOcrHintTrend(filteredCategoryEntries))
                .build();
    }

    private int resolveLookbackDays(Optional<Integer> requestedLookbackDays) {
        int value = requestedLookbackDays == null || requestedLookbackDays.isEmpty() ? 30 : requestedLookbackDays.get();
        if (value < 1) {
            return 1;
        }
        return Math.min(value, 365);
    }

    private List<MetadataUpdateHistoryEntryResponse> filterByLookback(List<MetadataUpdateHistoryEntryResponse> entries, Instant threshold) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }

        return entries.stream()
                .filter(entry -> parseInstant(entry.getUpdatedAt())
                        .map(updatedAt -> !updatedAt.isBefore(threshold))
                        .orElse(false))
                .toList();
    }

    private List<MetadataUpdateAdoptionTrendPointResponse> buildOcrHintTrend(List<MetadataUpdateHistoryEntryResponse> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }

        List<Instant> instants = entries.stream()
                .map(entry -> parseInstant(entry.getUpdatedAt()))
                .flatMap(Optional::stream)
                .sorted()
                .toList();

        if (instants.isEmpty()) {
            return List.of();
        }

        Instant anchor = instants.get(instants.size() - 1);
        List<MetadataUpdateAdoptionTrendPointResponse> trend = new ArrayList<>();
        for (int dayOffset = 2; dayOffset >= 0; dayOffset--) {
            Instant from = anchor.minus(Duration.ofDays(dayOffset)).truncatedTo(java.time.temporal.ChronoUnit.DAYS);
            Instant to = from.plus(Duration.ofDays(1));

            List<MetadataUpdateHistoryEntryResponse> bucket = entries.stream()
                    .filter(entry -> parseInstant(entry.getUpdatedAt())
                            .map(updatedAt -> !updatedAt.isBefore(from) && updatedAt.isBefore(to))
                            .orElse(false))
                    .toList();

            long total = bucket.size();
            long ocrHint = countBySource(bucket, "OCR_HINT");
            long ocrHintCancel = countBySources(bucket, List.of("OCR_HINT_CANCEL", "OCR_HINT_DISMISSED"));
            long ocrHintError = countBySource(bucket, "OCR_HINT_ERROR");
            trend.add(MetadataUpdateAdoptionTrendPointResponse.builder()
                    .label(from.toString().substring(0, 10))
                    .totalUpdates(total)
                    .ocrHintUpdates(ocrHint)
                    .ocrHintCancelUpdates(ocrHintCancel)
                    .ocrHintErrorUpdates(ocrHintError)
                    .ocrHintRate(resolveRatio(ocrHint, total))
                    .build());
        }

        return trend;
    }

    private long countBySource(List<MetadataUpdateHistoryEntryResponse> entries, String source) {
        if (entries == null || entries.isEmpty()) {
            return 0L;
        }

        return entries.stream()
                .filter(entry -> StringUtils.equalsIgnoreCase(StringUtils.trimToEmpty(entry.getSource()), StringUtils.trimToEmpty(source)))
                .count();
    }

    private long countBySources(List<MetadataUpdateHistoryEntryResponse> entries, List<String> sources) {
        if (entries == null || entries.isEmpty() || sources == null || sources.isEmpty()) {
            return 0L;
        }

        return entries.stream()
                .filter(entry -> sources.stream().anyMatch(source -> StringUtils.equalsIgnoreCase(StringUtils.trimToEmpty(entry.getSource()), StringUtils.trimToEmpty(source))))
                .count();
    }

    private double resolveRatio(long part, long total) {
        if (total <= 0L) {
            return 0.0d;
        }
        return part / (double) total;
    }

    private String resolvePersistedOcrExcerpt(DmsDocument document) {
        String ocrText = document == null ? "" : StringUtils.normalizeSpace(document.getOcrText());
        if (StringUtils.isBlank(ocrText)) {
            return null;
        }

        return ocrText.length() <= 220
                ? ocrText
                : ocrText.substring(0, 220) + "...";
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
                    "RAG de documento desabilitado por feature flag (dms.ai.rag.document.enabled=false).", "FEATURE_FLAG_DISABLED", List.of(), List.of(), startedAt);
        }

        if (!ragEnabledTenants.isEmpty() && !ragEnabledTenants.contains(tenantId)) {
            return buildRagResponse(documentId, version, tenantId, "", false, "TENANT_DISABLED",
                    "RAG de documento desabilitado para o tenant atual (allowlist não inclui este tenant).", "TENANT_NOT_ALLOWED", List.of(), List.of(), startedAt);
        }

        DmsDocument document = dmsDocumentRepository.findByIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> new DmsDocumentNotFoundException("Document not found", TypeException.VALID));
        String category = StringUtils.trimToEmpty(document.getCategory());

        if (!ragEnabledCategories.isEmpty()) {
            boolean allowedCategory = ragEnabledCategories.contains(StringUtils.lowerCase(category));
            if (!allowedCategory) {
                return buildRagResponse(documentId, version, tenantId, category, false, "CATEGORY_DISABLED",
                        "RAG de documento desabilitado para a categoria atual (allowlist por categoria).", "CATEGORY_NOT_ALLOWED", List.of(), List.of(), startedAt);
            }
        }

        List<String> missingRequiredMetadata = resolveMissingRequiredMetadata(document, resolveExpectedRequiredMetadata(tenantId, document));
        if (!missingRequiredMetadata.isEmpty()) {
            String missingPreview = missingRequiredMetadata.stream().limit(3).collect(Collectors.joining(", "));
            String suffix = missingRequiredMetadata.size() > 3 ? "..." : "";
            return buildRagResponse(documentId, version, tenantId, category, false, "QUALITY_GATED",
                    "RAG aguardando qualidade mínima: preencha metadados obrigatórios faltantes (" + missingPreview + suffix + ").", "REQUIRED_METADATA_MISSING", missingRequiredMetadata, List.of(), startedAt);
        }

        List<RagContextChunkResponse> chunks = buildChunks(document);
        return buildRagResponse(documentId, version, tenantId, category, true, "READY",
                chunks.isEmpty() ? "Sem chunks de OCR disponíveis para este documento." : "Contexto RAG local carregado.",
                "NONE",
                List.of(),
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
                                                        String rolloutGuard,
                                                        List<String> missingRequiredMetadata,
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
                .rolloutGuard(rolloutGuard)
                .featureFlagEnabled(ragEnabled)
                .tenantAllowed(ragEnabledTenants.isEmpty() || ragEnabledTenants.contains(tenantId))
                .categoryAllowed(ragEnabledCategories.isEmpty() || ragEnabledCategories.contains(StringUtils.lowerCase(StringUtils.defaultIfBlank(category, ""))))
                .missingRequiredMetadata(missingRequiredMetadata)
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
