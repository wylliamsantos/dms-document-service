package br.com.dms.service.workflow;

import br.com.dms.exception.DmsBusinessException;
import br.com.dms.exception.TypeException;
import br.com.dms.service.ValidatorCategoryService;
import br.com.dms.service.workflow.pojo.TypeFieldMetadata;
import br.com.dms.util.DmsUtil;
import com.networknt.schema.ValidationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MetadataService {
    private static final Logger logger = LoggerFactory.getLogger(MetadataService.class);
    private final DmsUtil dmsUtil;

    private final ValidatorCategoryService validatorCategoryService;

    private final Environment environment;

    public MetadataService(DmsUtil dmsUtil, ValidatorCategoryService validatorCategoryService, Environment environment) {
        this.dmsUtil = dmsUtil;
        this.validatorCategoryService = validatorCategoryService;
        this.environment = environment;
    }

    public Map<String, Object> getValideMetadata(String transactionId, String metadata, String categoryName, LocalDate issuingDate) {
        Map<String, Object> jsonMetadata = dmsUtil.handleObject(transactionId, metadata);
        return validateMetadata(transactionId, categoryName, issuingDate, jsonMetadata);
    }

    public Map<String, Object> getValideMetadata(String transactionId, Map<String, Object> metadata, String categoryName, LocalDate issuingDate) {
        Map<String, Object> jsonMetadata = metadata == null ? new HashMap<>() : new HashMap<>(metadata);
        return validateMetadata(transactionId, categoryName, issuingDate, jsonMetadata);
    }

    private Map<String, Object> validateMetadata(String transactionId,
                                                 String categoryName,
                                                 LocalDate issuingDate,
                                                 Map<String, Object> jsonMetadata) {
        Set<ValidationMessage> validationMessages;
        try {
            validationMessages = validatorCategoryService.validateCategory(categoryName, jsonMetadata, issuingDate, transactionId);
        } catch (IOException exception) {
            logger.error("DMS - TransactionId: {} - Error validating metadata for category {}", transactionId, categoryName, exception);
            throw new DmsBusinessException(environment.getProperty("dms.msg.jsonMetadataError"), TypeException.VALID, transactionId);
        }

        var typeErrors = validationMessages.stream()
                .filter(validationMessage -> validationMessage.getType().equalsIgnoreCase("type") &&
                        !validationMessage.getArguments()[0].equalsIgnoreCase("null"))
                .collect(Collectors.toList());

        if(!typeErrors.isEmpty()){
            logger.info("DMS - tipos inválidos no metadata: {} será realizado um cast para o tipo correto", categoryName);
            castTypes(typeErrors, jsonMetadata);
            typeErrors.forEach(validationMessages::remove);
        }

        if(!validationMessages.isEmpty()){
            var errorsMessages = validationMessages.stream().map(ValidationMessage::getMessage).collect(Collectors.joining(", "));
            var messageError = String.format("Metadata Invalido: [%s]", errorsMessages);
            logger.error("DMS - {}", messageError);
            throw new DmsBusinessException(messageError, TypeException.VALID, transactionId);
        }

        return jsonMetadata;
    }

    private void castTypes(List<ValidationMessage> typeErrors, Map<String, Object> jsonMetadata) {
        typeErrors.forEach(validationMessage -> {
            String key = validationMessage.getPath().substring(2);
            var value = jsonMetadata.get(key);

            if (value != null){
                String argument = validationMessage.getArguments()[1];
                jsonMetadata.put(key, TypeFieldMetadata.get(argument).cast(value));
            }
        });
    }
}
