package br.com.dms.service;

import br.com.dms.controller.response.MetadataSuggestionResponse;
import br.com.dms.domain.mongodb.Category;
import br.com.dms.domain.mongodb.DmsDocument;
import br.com.dms.domain.mongodb.DmsDocumentVersion;
import br.com.dms.exception.DmsBusinessException;
import br.com.dms.exception.TypeException;
import br.com.dms.repository.mongo.CategoryRepository;
import br.com.dms.repository.mongo.DmsDocumentRepository;
import br.com.dms.repository.mongo.DmsDocumentVersionRepository;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiMetadataSuggestionService {

    private final DmsDocumentRepository dmsDocumentRepository;
    private final DmsDocumentVersionRepository dmsDocumentVersionRepository;
    private final CategoryRepository categoryRepository;
    private final TenantContextService tenantContextService;

    public AiMetadataSuggestionService(DmsDocumentRepository dmsDocumentRepository,
                                       DmsDocumentVersionRepository dmsDocumentVersionRepository,
                                       CategoryRepository categoryRepository,
                                       TenantContextService tenantContextService) {
        this.dmsDocumentRepository = dmsDocumentRepository;
        this.dmsDocumentVersionRepository = dmsDocumentVersionRepository;
        this.categoryRepository = categoryRepository;
        this.tenantContextService = tenantContextService;
    }

    public MetadataSuggestionResponse suggest(String documentId, Optional<String> version) {
        String tenantId = tenantContextService.requireTenantId();

        DmsDocument document = dmsDocumentRepository.findByIdAndTenantId(documentId, tenantId)
            .orElseThrow(() -> new DmsBusinessException("Documento não encontrado", TypeException.VALID));

        DmsDocumentVersion documentVersion = version
            .flatMap(v -> dmsDocumentVersionRepository.findByTenantIdAndDmsDocumentIdAndVersionNumber(tenantId, documentId, v))
            .orElseGet(() -> dmsDocumentVersionRepository.findLastVersionByTenantIdAndDmsDocumentId(tenantId, documentId)
                .orElseThrow(() -> new DmsBusinessException("Versão do documento não encontrada", TypeException.VALID)));

        String textBase = String.join("\n", List.of(
            StringUtils.defaultString(document.getFilename()),
            StringUtils.defaultString(document.getOcrText())
        ));

        Map<String, Object> suggestions = new LinkedHashMap<>();

        categoryRepository.findByTenantIdAndName(tenantId, document.getCategory())
            .ifPresent(category -> collectFromSchema(suggestions, textBase, category));

        if (suggestions.isEmpty() && StringUtils.isNotBlank(document.getBusinessKeyType()) && StringUtils.isNotBlank(document.getBusinessKeyValue())) {
            suggestions.put(document.getBusinessKeyType(), document.getBusinessKeyValue());
        }

        double confidence = suggestions.isEmpty() ? 0.0 : Math.min(0.95d, 0.45d + (suggestions.size() * 0.1d));

        return MetadataSuggestionResponse.builder()
            .documentId(documentId)
            .version(documentVersion.getVersionNumber() == null ? null : documentVersion.getVersionNumber().toPlainString())
            .category(document.getCategory())
            .suggestedMetadata(suggestions)
            .confidence(confidence)
            .source("ocr+heuristics")
            .build();
    }

    @SuppressWarnings("unchecked")
    private void collectFromSchema(Map<String, Object> suggestions, String textBase, Category category) {
        if (category.getSchema() == null) {
            return;
        }
        Object propertiesRaw = category.getSchema().get("properties");
        if (!(propertiesRaw instanceof Map<?, ?> properties)) {
            return;
        }

        for (Map.Entry<?, ?> entry : properties.entrySet()) {
            String fieldKey = String.valueOf(entry.getKey());
            String fieldValue = extractFieldValue(textBase, fieldKey, entry.getValue());
            if (StringUtils.isNotBlank(fieldValue)) {
                suggestions.put(fieldKey, fieldValue.trim());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private String extractFieldValue(String textBase, String fieldKey, Object fieldDefinition) {
        String normalizedKey = StringUtils.lowerCase(StringUtils.defaultString(fieldKey));

        if (normalizedKey.contains("cpf")) {
            return regexFirst(textBase, "\\b\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}\\b");
        }
        if (normalizedKey.contains("cnpj")) {
            return regexFirst(textBase, "\\b\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2}\\b");
        }
        if (normalizedKey.contains("data") || normalizedKey.endsWith("date")) {
            return regexFirst(textBase, "\\b\\d{2}/\\d{2}/\\d{4}\\b");
        }
        if (normalizedKey.contains("valor") || normalizedKey.contains("amount") || normalizedKey.contains("total")) {
            return regexFirst(textBase, "\\b\\d{1,3}(?:\\.\\d{3})*,\\d{2}\\b");
        }

        List<String> labels = new ArrayList<>();
        labels.add(fieldKey);

        if (fieldDefinition instanceof Map<?, ?> definitionMap) {
            Object title = definitionMap.get("title");
            if (title != null) {
                labels.add(String.valueOf(title));
            }
        }

        for (String label : labels) {
            String value = regexFirst(textBase, "(?im)\\b" + Pattern.quote(label).replace("_", "[ _]") + "\\b\\s*[:\\-]\\s*([^\\n]{2,120})");
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }

        return null;
    }

    private String regexFirst(String text, String pattern) {
        Matcher matcher = Pattern.compile(pattern).matcher(StringUtils.defaultString(text));
        if (matcher.find()) {
            return matcher.group(matcher.groupCount() >= 1 ? 1 : 0);
        }
        return null;
    }
}
