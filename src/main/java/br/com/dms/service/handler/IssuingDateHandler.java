package br.com.dms.service.handler;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import br.com.dms.domain.core.DocumentCategory;
import br.com.dms.exception.DmsBusinessException;
import br.com.dms.exception.TypeException;

@Component
public class IssuingDateHandler {

	private static final Logger logger = LoggerFactory.getLogger(IssuingDateHandler.class);

	protected final Environment environment;

	protected final PrefixHandler prefixHandler;

	public IssuingDateHandler(Environment environment, PrefixHandler prefixHandler) {
		this.environment = environment;
		this.prefixHandler = prefixHandler;
	}

	public void handle(String transactionId, LocalDate issuingDate, DocumentCategory documentCategory,
			Map<String, Object> jsonMetadata) {
		if (documentCategory.getConditionalValidityInDays() != null) {
			if (issuingDate == null) {
				logger.info("DMS - TransactionId: {} - issuing date is mandatory for this document category {}  and document type {}", transactionId,
						documentCategory.getName(), documentCategory.getDocumentType().getName());
				throw new DmsBusinessException(environment.getProperty("dms.msg.issuingDateIsMandatory"), TypeException.VALID, transactionId);
			}
			LocalDate expirationDate = issuingDate.plusDays(documentCategory.getConditionalValidityInDays());

			if (LocalDate.now().isAfter(expirationDate)) {
				logger.info("DMS - TransactionId: {} - Invalid issuing date {}, expiration date {}", transactionId, issuingDate, expirationDate);
				throw new DmsBusinessException(environment.getProperty("dms.msg.invalidIssuingDate"), TypeException.VALID, transactionId);
			}

			jsonMetadata.put(prefixHandler.handle(documentCategory.getPrefix(), "dataExpiracao"), expirationDate.toString());
			jsonMetadata.put(prefixHandler.handle(documentCategory.getPrefix(), "dataEmissao"), issuingDate.toString());

			logger.info("DMS - TransactionId: {} - Adding expiration {{}} and issuing dates {{}} to alfresco metadata", transactionId, expirationDate,
					issuingDate);

		}
	}
	
}
