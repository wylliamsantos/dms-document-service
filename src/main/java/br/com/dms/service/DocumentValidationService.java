package br.com.dms.service;

import br.com.dms.exception.DmsBusinessException;
import br.com.dms.exception.TypeException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class DocumentValidationService {

    private final Environment environment;

    public DocumentValidationService(Environment environment) {
        this.environment = environment;
    }

    public void validateAuthor(String transactionId, String author) {
        if (StringUtils.isBlank(author)) {
            throw businessException("dms.msg.authorIsMandatory", "Author é obrigatório", transactionId);
        }
    }

    public void validateFilename(String transactionId, String filename) {
        if (StringUtils.isBlank(filename)) {
            throw businessException("dms.msg.filenameIsMandatory", "Nome do arquivo é obrigatório", transactionId);
        }
    }

    public void validateCategory(String transactionId, String category) {
        if (StringUtils.isBlank(category)) {
            throw businessException("dms.msg.categoryIsMandatory", "Categoria é obrigatória", transactionId);
        }
    }

    private DmsBusinessException businessException(String property, String fallbackMessage, String transactionId) {
        String message = environment.getProperty(property, fallbackMessage);
        return new DmsBusinessException(message, TypeException.VALID, transactionId);
    }
}
