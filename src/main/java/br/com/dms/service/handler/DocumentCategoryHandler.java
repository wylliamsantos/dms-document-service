package br.com.dms.service.handler;

import br.com.dms.domain.mongodb.Category;
import br.com.dms.domain.mongodb.CategoryType;
import br.com.dms.domain.core.DocumentCategory;
import br.com.dms.domain.core.DocumentType;
import br.com.dms.repository.mongo.CategoryRepository;
import br.com.dms.exception.DmsBusinessException;
import br.com.dms.exception.TypeException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@CacheConfig(cacheNames = {"documentCategory", "documentType"})
@Slf4j
public class DocumentCategoryHandler {

    private static final String DOCUMENT_TYPE_METADATA_KEY = "tipoDocumento";

    private final CategoryRepository categoryRepository;

    private final Environment environment;

    public DocumentCategoryHandler(CategoryRepository categoryRepository,
                                   Environment environment) {
        this.categoryRepository = categoryRepository;
        this.environment = environment;
    }

    public DocumentCategory loadCategory(String transactionId, String documentCategoryName) {
        return findCategory(transactionId, documentCategoryName);
    }

    public DocumentCategory loadCategoryWithType(String transactionId, String documentCategoryName, String documentTypeName) {
        DocumentCategory documentCategory = findCategory(transactionId, documentCategoryName);
        if (StringUtils.isBlank(documentTypeName)) {
            return documentCategory;
        }
        DocumentType documentType = findDocumentType(transactionId, documentCategoryName, documentTypeName);
        documentCategory.setDocumentType(documentType);
        return documentCategory;
    }

    @Cacheable(cacheNames = "documentCategory", key = "#documentCategoryName.toLowerCase()")
    protected DocumentCategory findCategory(String transactionId, String documentCategoryName) {
        Category category = categoryRepository.findByName(documentCategoryName)
                .orElseThrow(() -> {
                    log.info("DMS - TransactionId: {} - Type invalid {}", transactionId, documentCategoryName);
                    return new DmsBusinessException(environment.getProperty("dms.msg.typeInvalid"), TypeException.VALID, transactionId);
                });

        DocumentCategory documentCategory = new DocumentCategory();
        documentCategory.setId(null);
        documentCategory.setName(category.getName());
        documentCategory.setDescription(category.getDescription());
        documentCategory.setUniqueAttributes(category.getUniqueAttributes());
        documentCategory.setValidityInDays(category.getValidityInDays());
        documentCategory.setDocumentGroup(category.getDocumentGroup());
        return documentCategory;
    }

    @Cacheable(cacheNames = "documentType", key = "#documentCategoryName.toLowerCase() + '::' + #documentTypeName.toLowerCase()")
    protected DocumentType findDocumentType(String transactionId, String documentCategoryName, String documentTypeName) {
        Category category = categoryRepository.findByName(documentCategoryName)
                .orElseThrow(() -> new DmsBusinessException(environment.getProperty("dms.msg.typeInvalid"), TypeException.VALID, transactionId));

        if (category.getTypes() == null) {
            log.info("DMS - TransactionId: {} - Document type {} not configured for category {}", transactionId, documentTypeName, documentCategoryName);
            throw new DmsBusinessException(environment.getProperty("dms.msg.typeInvalid"), TypeException.VALID, transactionId);
        }

        DocumentType documentType = category.getTypes().stream()
                .filter(type -> StringUtils.equalsIgnoreCase(type.getName(), documentTypeName))
                .findFirst()
                .map(this::mapDocumentType)
                .orElse(null);

        if (documentType == null) {
            log.info("DMS - TransactionId: {} - Document type {} not found for category {}", transactionId, documentTypeName, documentCategoryName);
            throw new DmsBusinessException(environment.getProperty("dms.msg.typeInvalid"), TypeException.VALID, transactionId);
        }

        return documentType;
    }

    private DocumentType mapDocumentType(CategoryType categoryType) {
        DocumentType documentType = new DocumentType();
        documentType.setId(null);
        documentType.setName(categoryType.getName());
        documentType.setDescription(categoryType.getDescription());
        documentType.setValidityInDays(categoryType.getValidityInDays());
        documentType.setRequiredAttributes(categoryType.getRequiredAttributes());
        return documentType;
    }

    public DocumentCategory resolveCategory(String transactionId, String documentCategoryName, Map<String, Object> metadata) {
        DocumentCategory documentCategory = loadCategory(transactionId, documentCategoryName);
        String documentTypeName = extractDocumentTypeFromMetadata(documentCategory, metadata);

        if (StringUtils.isNotBlank(documentTypeName)) {
            return loadCategoryWithType(transactionId, documentCategoryName, documentTypeName);
        }

        return documentCategory;
    }

    private String extractDocumentTypeFromMetadata(DocumentCategory documentCategory, Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }

        Set<String> candidateKeys = new LinkedHashSet<>();
        candidateKeys.add(DOCUMENT_TYPE_METADATA_KEY);
        candidateKeys.add(DOCUMENT_TYPE_METADATA_KEY.toLowerCase(Locale.ROOT));
        candidateKeys.add(capitalizeFirstLetter(DOCUMENT_TYPE_METADATA_KEY));

        for (String key : candidateKeys) {
            Object rawValue = metadata.get(key);
            if (rawValue == null) {
                rawValue = metadata.get(key.toLowerCase(Locale.ROOT));
            }

            if (rawValue instanceof String value && StringUtils.isNotBlank(value)) {
                return value;
            }
        }

        return metadata.entrySet().stream()
                .filter(entry -> entry.getKey() != null)
                .filter(entry -> entry.getValue() instanceof String)
                .map(entry -> (String) entry.getValue())
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse(null);
    }

    private String capitalizeFirstLetter(String value) {
        if (StringUtils.isBlank(value)) {
            return value;
        }

        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

}
