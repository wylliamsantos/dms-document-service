package br.com.dms.service.handler;

import br.com.dms.domain.mongodb.Category;
import br.com.dms.domain.mongodb.CategoryType;
import br.com.dms.domain.core.DocumentCategory;
import br.com.dms.domain.core.DocumentType;
import br.com.dms.repository.mongo.CategoryRepository;
import br.com.dms.exception.DmsBusinessException;
import br.com.dms.exception.TypeException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@CacheConfig(cacheNames = {"documentCategory", "documentType"})
public class DocumentCategoryHandler {

    private static final Logger logger = LoggerFactory.getLogger(DocumentCategoryHandler.class);

    private final CategoryRepository categoryRepository;

    private final PrefixHandler prefixHandler;

    private final Environment environment;

    public DocumentCategoryHandler(CategoryRepository categoryRepository,
                                   PrefixHandler prefixHandler,
                                   Environment environment) {
        this.categoryRepository = categoryRepository;
        this.prefixHandler = prefixHandler;
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
                    logger.info("DMS - TransactionId: {} - Type invalid {}", transactionId, documentCategoryName);
                    return new DmsBusinessException(environment.getProperty("dms.msg.typeInvalid"), TypeException.VALID, transactionId);
                });

        DocumentCategory documentCategory = new DocumentCategory();
        documentCategory.setId(null);
        documentCategory.setName(category.getName());
        documentCategory.setDescription(category.getDescription());
        documentCategory.setPrefix(category.getPrefix());
        documentCategory.setMainType(category.getMainType());
        documentCategory.setTypeSearch(category.getTypeSearch());
        documentCategory.setUniqueAttributes(category.getUniqueAttributes());
        documentCategory.setSearchDuplicateCriteria(category.getSearchDuplicateCriteria());
        documentCategory.setPath(category.getPath());
        documentCategory.setValidityInDays(category.getValidityInDays());
        documentCategory.setDocumentGroup(category.getDocumentGroup());
        documentCategory.setSite(category.getSite());
        documentCategory.setParentFolder(category.getParentFolder());
        return documentCategory;
    }

    @Cacheable(cacheNames = "documentType", key = "#documentCategoryName.toLowerCase() + '::' + #documentTypeName.toLowerCase()")
    protected DocumentType findDocumentType(String transactionId, String documentCategoryName, String documentTypeName) {
        Category category = categoryRepository.findByName(documentCategoryName)
                .orElseThrow(() -> new DmsBusinessException(environment.getProperty("dms.msg.typeInvalid"), TypeException.VALID, transactionId));

        if (category.getTypes() == null) {
            logger.info("DMS - TransactionId: {} - Document type {} not configured for category {}", transactionId, documentTypeName, documentCategoryName);
            throw new DmsBusinessException(environment.getProperty("dms.msg.typeInvalid"), TypeException.VALID, transactionId);
        }

        DocumentType documentType = category.getTypes().stream()
                .filter(type -> StringUtils.equalsIgnoreCase(type.getName(), documentTypeName))
                .findFirst()
                .map(this::mapDocumentType)
                .orElse(null);

        if (documentType == null) {
            logger.info("DMS - TransactionId: {} - Document type {} not found for category {}", transactionId, documentTypeName, documentCategoryName);
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
        String documentTypeKey = resolveMetadataDocumentTypeKey(documentCategory);
        Object rawType = metadata.get(documentTypeKey);
        if (rawType == null) {
            rawType = metadata.get(documentTypeKey.toLowerCase());
        }
        String documentTypeName = rawType instanceof String ? (String) rawType : null;

        if (StringUtils.isNotBlank(documentTypeName)) {
            documentCategory = loadCategoryWithType(transactionId, documentCategoryName, documentTypeName);
        } else if (StringUtils.isNotBlank(documentCategory.getMainType())) {
            documentCategory = loadCategoryWithType(transactionId, documentCategoryName, documentCategory.getMainType());
        }

        if (StringUtils.isBlank(documentCategory.getMainType()) && documentCategory.getDocumentType() == null) {
            logger.info("DMS - TransactionId: {} - Type invalid {}", transactionId, documentCategoryName);
            throw new DmsBusinessException(environment.getProperty("dms.msg.typeInvalid"), TypeException.VALID, transactionId);
        }

        return documentCategory;
    }

    public String resolveDocumentTypeName(DocumentCategory documentCategory, String documentTypeFromMetadata) {
        if (StringUtils.isNotBlank(documentTypeFromMetadata)) {
            return documentTypeFromMetadata;
        }
        if (StringUtils.isNotBlank(documentCategory.getMainType())) {
            return documentCategory.getMainType();
        }
        return null;
    }

    public String resolveMetadataDocumentTypeKey(DocumentCategory documentCategory) {
        return prefixHandler.handle(documentCategory.getPrefix(), "tipoDocumento");
    }
}
