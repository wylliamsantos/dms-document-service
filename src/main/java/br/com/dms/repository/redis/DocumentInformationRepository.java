package br.com.dms.repository.redis;

import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class DocumentInformationRepository {

    private static final Logger logger = LoggerFactory.getLogger(DocumentInformationRepository.class);

    private final RedisTemplate<String, String> documentInformationCache;

    private final Long cacheTimeToLive;

    public DocumentInformationRepository(RedisTemplate<String, String> documentInformationCache,
                                         @Value("${getInformation.cache.timeToLive:300}") Long cacheTimeToLive) {
        this.documentInformationCache = documentInformationCache;
        this.cacheTimeToLive = cacheTimeToLive;
    }

    private String getCacheKey(String documentId, String version) {
        return "dms-document-service:documentInformation:".concat(Strings.isBlank(version) ? documentId : documentId.concat("-").concat(version));
    }

    public String save(String documentId, String version, String value) {
        try {
            String key = getCacheKey(documentId, version);
            documentInformationCache.opsForValue().set(key, value, Duration.ofSeconds(cacheTimeToLive));
            return key;
        } catch (Exception exception) {
            logger.error("Erro ao acessar salvar no redis", exception);
            return null;
        }
    }

    public Optional<String> get(String documentId, String version) {
        try {
            return Optional.ofNullable(documentInformationCache.opsForValue().get(getCacheKey(documentId, version)));
        } catch (Exception exception) {
            logger.error("Erro ao acessar o redis para consultar a chave, irá acessar o alfresco diretamente", exception);
            return Optional.empty();
        }
    }

    public void delete(String documentId, String version) {
        try {
            documentInformationCache.delete(getCacheKey(documentId, version));
        } catch (Exception exception) {
            logger.error("Erro ao deletar cache do redis", exception);
        }
    }
}
