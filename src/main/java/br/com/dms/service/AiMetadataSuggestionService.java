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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AiMetadataSuggestionService {

    private static final Set<String> STOPWORDS = Set.of(
        "de", "da", "do", "das", "dos", "e", "a", "o", "as", "os", "em", "para", "com", "sem"
    );

    private static final DateTimeFormatter BRAZILIAN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

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

        String suggestedCategory = suggestCategoryName(tenantId, textBase).orElse(document.getCategory());
        List<String> consistencyWarnings = buildConsistencyWarnings(document, suggestedCategory, suggestions);

        double baseConfidence = suggestions.isEmpty() ? 0.0 : Math.min(0.95d, 0.45d + (suggestions.size() * 0.1d));
        double confidence = Math.max(0.0d, baseConfidence - Math.min(0.35d, consistencyWarnings.size() * 0.07d));

        return MetadataSuggestionResponse.builder()
            .documentId(documentId)
            .version(documentVersion.getVersionNumber() == null ? null : documentVersion.getVersionNumber().toPlainString())
            .category(document.getCategory())
            .suggestedCategory(suggestedCategory)
            .suggestedMetadata(suggestions)
            .summary(buildSummary(textBase, suggestions, consistencyWarnings))
            .consistencyWarnings(consistencyWarnings)
            .confidence(confidence)
            .source("ocr+heuristics")
            .build();
    }

    private String buildSummary(String textBase, Map<String, Object> suggestions, List<String> consistencyWarnings) {
        String textSnippet = Arrays.stream(StringUtils.defaultString(textBase).split("\\R"))
            .map(StringUtils::trimToNull)
            .filter(Objects::nonNull)
            .filter(line -> line.length() > 3)
            .limit(2)
            .collect(Collectors.joining(" | "));

        if (textSnippet.length() > 160) {
            textSnippet = textSnippet.substring(0, 157) + "...";
        }

        String metadataPreview = suggestions.entrySet().stream()
            .limit(3)
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining(", "));

        String warningsInfo = consistencyWarnings.isEmpty() ? "sem alertas" : consistencyWarnings.size() + " alerta(s)";

        if (StringUtils.isBlank(textSnippet) && StringUtils.isBlank(metadataPreview)) {
            return "Sem conteúdo OCR suficiente para resumir.";
        }

        return String.format(Locale.ROOT, "Resumo OCR: %s. Campos sugeridos: %s. Consistência: %s.",
            StringUtils.defaultIfBlank(textSnippet, "n/a"),
            StringUtils.defaultIfBlank(metadataPreview, "n/a"),
            warningsInfo);
    }

    private List<String> buildConsistencyWarnings(DmsDocument document,
                                                  String suggestedCategory,
                                                  Map<String, Object> suggestions) {
        List<String> warnings = new ArrayList<>();

        if (StringUtils.isNotBlank(document.getCategory())
            && StringUtils.isNotBlank(suggestedCategory)
            && !StringUtils.equals(document.getCategory(), suggestedCategory)) {
            warnings.add("Categoria atual difere da categoria sugerida por OCR");
        }

        Optional<String> cpfValue = findFirstByKeyContains(suggestions, "cpf");
        cpfValue.ifPresent(value -> {
            if (!isValidCpf(value)) {
                warnings.add("CPF extraído possui formato/dígitos inválidos");
            }
        });

        Optional<String> cnpjValue = findFirstByKeyContains(suggestions, "cnpj");
        cnpjValue.ifPresent(value -> {
            if (!isValidCnpj(value)) {
                warnings.add("CNPJ extraído possui formato/dígitos inválidos");
            }
        });

        if (cpfValue.isPresent() && cnpjValue.isPresent()) {
            warnings.add("Documento contém CPF e CNPJ ao mesmo tempo (verificar contexto)");
        }

        findFirstByKeyContains(suggestions, "data").ifPresent(value -> {
            try {
                LocalDate parsed = LocalDate.parse(value, BRAZILIAN_DATE);
                if (parsed.isAfter(LocalDate.now().plusDays(1))) {
                    warnings.add("Data extraída está no futuro");
                }
            } catch (Exception ignored) {
                warnings.add("Data extraída não segue formato dd/MM/yyyy");
            }
        });

        findFirstByAnyKeyContains(suggestions, List.of("valor", "amount", "total")).ifPresent(value -> {
            double normalizedValue = parseMonetaryValue(value);
            if (normalizedValue <= 0.0d) {
                warnings.add("Valor monetário extraído é zero/negativo");
            }
        });

        return warnings;
    }

    private Optional<String> findFirstByAnyKeyContains(Map<String, Object> suggestions, List<String> fragments) {
        for (String fragment : fragments) {
            Optional<String> value = findFirstByKeyContains(suggestions, fragment);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    private Optional<String> findFirstByKeyContains(Map<String, Object> suggestions, String keyFragment) {
        return suggestions.entrySet().stream()
            .filter(entry -> StringUtils.containsIgnoreCase(entry.getKey(), keyFragment))
            .map(Map.Entry::getValue)
            .filter(Objects::nonNull)
            .map(String::valueOf)
            .findFirst();
    }

    private double parseMonetaryValue(String value) {
        String normalized = StringUtils.defaultString(value)
            .replace(".", "")
            .replace(",", ".")
            .replaceAll("[^0-9.-]", "");

        try {
            return Double.parseDouble(normalized);
        } catch (Exception ignored) {
            return 0.0d;
        }
    }

    private boolean isValidCpf(String value) {
        String digits = StringUtils.defaultString(value).replaceAll("\\D", "");
        if (digits.length() != 11 || digits.chars().distinct().count() == 1) {
            return false;
        }

        int firstDigit = calculateBrazilianCheckDigit(digits.substring(0, 9), 10);
        int secondDigit = calculateBrazilianCheckDigit(digits.substring(0, 9) + firstDigit, 11);

        return digits.equals(digits.substring(0, 9) + firstDigit + secondDigit);
    }

    private boolean isValidCnpj(String value) {
        String digits = StringUtils.defaultString(value).replaceAll("\\D", "");
        if (digits.length() != 14 || digits.chars().distinct().count() == 1) {
            return false;
        }

        int firstDigit = calculateCnpjDigit(digits.substring(0, 12));
        int secondDigit = calculateCnpjDigit(digits.substring(0, 12) + firstDigit);

        return digits.equals(digits.substring(0, 12) + firstDigit + secondDigit);
    }

    private int calculateBrazilianCheckDigit(String base, int weightStart) {
        int sum = 0;
        for (int i = 0; i < base.length(); i++) {
            sum += Character.getNumericValue(base.charAt(i)) * (weightStart - i);
        }

        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }

    private int calculateCnpjDigit(String base) {
        int[] weights = base.length() == 12
            ? new int[]{5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2}
            : new int[]{6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

        int sum = 0;
        for (int i = 0; i < base.length(); i++) {
            sum += Character.getNumericValue(base.charAt(i)) * weights[i];
        }

        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }

    private Optional<String> suggestCategoryName(String tenantId, String textBase) {
        List<Category> categories = categoryRepository.findAllByTenantId(tenantId);
        if (categories.isEmpty() || StringUtils.isBlank(textBase)) {
            return Optional.empty();
        }

        Set<String> documentTokens = tokenize(textBase);
        if (documentTokens.isEmpty()) {
            return Optional.empty();
        }

        String bestCategory = null;
        double bestScore = 0.0;

        for (Category category : categories) {
            Set<String> categoryTokens = tokensFromCategory(category);
            if (categoryTokens.isEmpty()) {
                continue;
            }

            long overlap = categoryTokens.stream().filter(documentTokens::contains).count();
            if (overlap == 0) {
                continue;
            }

            double score = (double) overlap / (double) categoryTokens.size();
            if (score > bestScore) {
                bestScore = score;
                bestCategory = category.getName();
            }
        }

        return bestScore >= 0.20d ? Optional.ofNullable(bestCategory) : Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private Set<String> tokensFromCategory(Category category) {
        Set<String> tokens = new LinkedHashSet<>();

        tokens.addAll(tokenize(category.getName()));
        tokens.addAll(tokenize(category.getTitle()));
        tokens.addAll(tokenize(category.getDescription()));

        if (category.getSchema() != null) {
            Object propertiesRaw = category.getSchema().get("properties");
            if (propertiesRaw instanceof Map<?, ?> properties) {
                for (Map.Entry<?, ?> property : properties.entrySet()) {
                    tokens.addAll(tokenize(String.valueOf(property.getKey())));
                    Object definition = property.getValue();
                    if (definition instanceof Map<?, ?> definitionMap) {
                        Object title = definitionMap.get("title");
                        if (title != null) {
                            tokens.addAll(tokenize(String.valueOf(title)));
                        }
                    }
                }
            }
        }

        return tokens;
    }

    private Set<String> tokenize(String value) {
        return Arrays.stream(StringUtils.defaultString(value).toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
            .map(StringUtils::trimToNull)
            .filter(Objects::nonNull)
            .filter(token -> token.length() > 2)
            .filter(token -> !STOPWORDS.contains(token))
            .collect(Collectors.toCollection(LinkedHashSet::new));
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
