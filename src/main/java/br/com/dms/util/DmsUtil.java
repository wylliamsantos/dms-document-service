
package br.com.dms.util;

import br.com.dms.domain.mongodb.Category;
import br.com.dms.exception.DmsBusinessException;
import br.com.dms.exception.DmsException;
import br.com.dms.exception.TypeException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MimeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

@Service
public class DmsUtil {

	private static final Logger logger = LoggerFactory.getLogger(DmsUtil.class);

	private final Environment environment;

	public DmsUtil(Environment environment) {
		this.environment = environment;
	}

	public MimeType validateMimeType(String transactionId, ByteArrayInputStream documentData) {
		TikaConfig tikaConfig;
		org.apache.tika.mime.MediaType mediaType;
		MimeType mimeType;
		try {
			tikaConfig = new TikaConfig();
			mediaType = tikaConfig.getMimeRepository().detect(documentData, new Metadata());
			mimeType = tikaConfig.getMimeRepository().forName(mediaType.toString());
		} catch (TikaException | IOException e1) {
			logger.error("DMS - TransactionId: {} - Error to check mime type of document", transactionId, e1);
			throw new DmsException(environment.getProperty("dms.msg.unknowError"), TypeException.CONFIG, transactionId);
		}

		if (!Objects.requireNonNull(environment.getProperty("dms.contentTypes")).contains(mimeType.getName())) {
			logger.info("DMS - TransactionId: {} - MIME type invalid: {}", transactionId, mimeType.getName());
			throw new DmsBusinessException(environment.getProperty("dms.msg.mimeTypeInvalid"), TypeException.VALID, transactionId);
		}

		return mimeType;
	}

	public BigDecimal generateVersion(boolean isFinal, BigDecimal version) {

		if (isFinal) {
			version = new BigDecimal(version.intValue() + 1).setScale(1);
		} else {
			int intValue = version.intValue();
			BigDecimal decimalValue = version.subtract(new BigDecimal(intValue));
			decimalValue = new BigDecimal(decimalValue.unscaledValue().add(BigInteger.ONE));
			var newVersion = new BigDecimal(intValue);
			version = newVersion.add(decimalValue.movePointLeft(decimalValue.precision()));
		}

		return version;
	}

	public BigDecimal generateNewMajorVersion(BigDecimal lastVersion) {
		return generateVersion(true, lastVersion);
	}

	public String getBusinessKeyFromMetadata(Map<String, Object> jsonMetadata, String preferredKey) {
		String effectivePreferred = StringUtils.trimToNull(preferredKey);
		if (effectivePreferred == null) {
			throw new DmsBusinessException("Tipo da chave de negócio não configurado para a categoria", TypeException.VALID);
		}
		String key = jsonMetadata.keySet()
				.stream()
				.filter(candidate -> StringUtils.equalsIgnoreCase(candidate, effectivePreferred)
						|| StringUtils.containsIgnoreCase(candidate, effectivePreferred))
				.findFirst()
				.orElseThrow(() -> new DmsBusinessException(String.format("Chave de negócio não informada nos metadados (%s)", effectivePreferred), TypeException.VALID));

		Object value = jsonMetadata.get(key);
		if (value == null || StringUtils.isBlank(value.toString())) {
			throw new DmsBusinessException(String.format("Valor da chave de negócio inválido (%s)", effectivePreferred), TypeException.VALID);
		}

		return value.toString().trim();
	}

	public String getCpfFromMetadata(Map<String, Object> jsonMetadata) {
		return getBusinessKeyFromMetadata(jsonMetadata, "cpf");
	}

	public String getCpfFromMetadata(Map<String, Object> jsonMetadata, String keyCpf) {
		return getBusinessKeyFromMetadata(jsonMetadata, keyCpf);
	}

	public String getCpfKeyRequired(Category category){
		List<String> fieldsRequired = (List<String>) category.getSchema().getOrDefault("required", Collections.EMPTY_LIST);
        return fieldsRequired.stream().filter(key -> StringUtils.containsIgnoreCase(key, "cpf")).findFirst().orElse("cpf");
	}

	public HashMap<String, Object> handleObject(String transactionId, String metadata) {
		try {
			return new ObjectMapper().readValue(metadata, new TypeReference<>() {
			});

		} catch (Exception exception) {
			logger.info("DMS TransactionId {} - Fail to parse json input metadata: {}", transactionId, metadata);
			throw new DmsBusinessException(environment.getProperty("dms.msg.jsonMetadataError"), TypeException.VALID);
		}
	}

	public Map<String, Object> handleObject(Map<String, Object> metadados) {
		if (metadados == null) {
			return new HashMap<>();
		}
		return new HashMap<>(metadados);
	}

	public byte[] decodeBase64(String transactionId, String base64Content) {
		if (StringUtils.isBlank(base64Content) || !Base64.isBase64(base64Content)) {
			throw new DmsBusinessException(environment.getProperty("dms.msg.base64Invalid"), TypeException.VALID, transactionId);
		}

		return Base64.decodeBase64(base64Content);
	}
}
