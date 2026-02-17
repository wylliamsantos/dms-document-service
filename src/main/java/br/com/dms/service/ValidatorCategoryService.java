package br.com.dms.service;

import br.com.dms.domain.mongodb.Category;
import br.com.dms.exception.DmsBusinessException;
import br.com.dms.exception.TypeException;
import br.com.dms.repository.mongo.CategoryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import static br.com.dms.domain.Messages.*;

@Service
@Slf4j
public class ValidatorCategoryService {
    private final CategoryRepository categoryRepository;
    private final TenantContextService tenantContextService;

    public ValidatorCategoryService(CategoryRepository categoryRepository,
                                    TenantContextService tenantContextService) {
        this.categoryRepository = categoryRepository;
        this.tenantContextService = tenantContextService;
    }

    public Set<ValidationMessage> validateCategory(String categoryName, Map<String, Object> jsonMetadata, LocalDate issuingDate, String transactionId) throws IOException {
        log.info("DMS - Validar categoria: {}", categoryName);
        String tenantId = tenantContextService.requireTenantId();
        var category = this.categoryRepository.findByTenantIdAndName(tenantId, categoryName)
                .orElseThrow(() -> {
                    log.error("DMS - Categoria {} não encontrada para tenant {}", categoryName, tenantId);
                    throw new DmsBusinessException(CATEGORY_NOT_FOUND, TypeException.VALID, transactionId);
                });


        var schema = getJsonSchema(category.getSchema());
        handleIssuingDate(issuingDate, category, jsonMetadata);
        var jsonContent = getJsonNodeFromMapContent(jsonMetadata);
        return validate(schema, jsonContent);
    }

    private Set<ValidationMessage> validate(JsonSchema jsonSchema, JsonNode json) {
        return jsonSchema.validate(json);
    }

    private void handleIssuingDate(LocalDate issuingDate, Category documentCategory, Map<String, Object> jsonMetadata) {
        if (documentCategory.getValidityInDays() != null) {
            if (issuingDate == null) {
                log.error(ISSUING_DATE_IS_MANDATORY);
                throw new DmsBusinessException(ISSUING_DATE_IS_MANDATORY, TypeException.VALID);
            }
            LocalDate expirationDate = issuingDate.plusDays(documentCategory.getValidityInDays());

            if (LocalDate.now().isAfter(expirationDate)) {
                log.error(INVALID_DATE);
                throw new DmsBusinessException(INVALID_DATE, TypeException.VALID);
            }

            jsonMetadata.put("dataExpiracao", expirationDate.toString());
            jsonMetadata.put("dataEmissao", issuingDate.toString());
        }
    }

    private JsonSchema getJsonSchema(Map<Object, Object> schemaMap) {
        ObjectMapper mapper = new ObjectMapper();
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V4);
        var jsonNode = mapper.convertValue(schemaMap, JsonNode.class);
        return factory.getSchema(jsonNode);
    }

    private JsonNode getJsonNodeFromMapContent(Map<String, Object> schemaMap) {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.convertValue(schemaMap, JsonNode.class);
    }


}
