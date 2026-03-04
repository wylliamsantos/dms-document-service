package br.com.dms.service;

import br.com.dms.domain.mongodb.DmsDocument;
import br.com.dms.domain.mongodb.OcrProcessingDlq;
import br.com.dms.repository.mongo.DmsDocumentRepository;
import br.com.dms.repository.mongo.OcrProcessingDlqRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;

@Service
@Slf4j
public class OcrPipelineService {

    private final AmazonS3Service amazonS3Service;
    private final DmsDocumentRepository dmsDocumentRepository;
    private final OcrProcessingDlqRepository ocrProcessingDlqRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${dms.ocr.enabled:true}")
    private boolean enabled;

    @Value("${dms.ocr.base-url:http://ocr-service:8000}")
    private String ocrBaseUrl;

    @Value("${dms.ocr.max-attempts:3}")
    private int maxAttempts;

    @Value("${dms.ocr.retry-delay-ms:1500}")
    private long retryDelayMs;

    @Value("${dms.ocr.max-pages:3}")
    private int maxPages;

    public OcrPipelineService(AmazonS3Service amazonS3Service,
                              DmsDocumentRepository dmsDocumentRepository,
                              OcrProcessingDlqRepository ocrProcessingDlqRepository) {
        this.amazonS3Service = amazonS3Service;
        this.dmsDocumentRepository = dmsDocumentRepository;
        this.ocrProcessingDlqRepository = ocrProcessingDlqRepository;
    }

    @Async
    public void processAsync(String tenantId, String documentId, String version, String pathToDocument, String mimeType, String filename) {
        if (!enabled || !supportsMimeType(mimeType) || StringUtils.isBlank(pathToDocument)) {
            return;
        }

        Exception lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String text = runOcr(pathToDocument, filename);
                if (StringUtils.isBlank(text)) {
                    return;
                }

                dmsDocumentRepository.findByIdAndTenantId(documentId, tenantId).ifPresent(document -> {
                    document.setOcrText(text);
                    dmsDocumentRepository.save(document);
                });
                return;
            } catch (Exception ex) {
                lastError = ex;
                log.warn("OCR falhou para doc={} versao={} tentativa={}/{}", documentId, version, attempt, maxAttempts, ex);
                if (attempt < maxAttempts) {
                    sleepBeforeRetry();
                }
            }
        }

        OcrProcessingDlq dlq = new OcrProcessingDlq();
        dlq.setTenantId(tenantId);
        dlq.setDocumentId(documentId);
        dlq.setVersion(version);
        dlq.setPathToDocument(pathToDocument);
        dlq.setMimeType(mimeType);
        dlq.setAttempts(maxAttempts);
        dlq.setError(lastError == null ? "OCR failure" : StringUtils.left(lastError.getMessage(), 2000));
        dlq.setCreatedAt(LocalDateTime.now());
        ocrProcessingDlqRepository.save(dlq);
    }

    private boolean supportsMimeType(String mimeType) {
        if (StringUtils.isBlank(mimeType)) {
            return false;
        }
        String normalized = mimeType.toLowerCase(Locale.ROOT);
        return normalized.equals("application/pdf") || normalized.startsWith("image/");
    }

    private String runOcr(String pathToDocument, String filename) {
        byte[] fileBytes;
        try {
            fileBytes = amazonS3Service.getDocumentContentFromS3(pathToDocument);
        } catch (Exception ex) {
            throw new RuntimeException("Erro ao baixar documento no S3 para OCR", ex);
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return StringUtils.defaultIfBlank(filename, "document.bin");
            }
        });

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<Map> response = restTemplate.exchange(
                ocrBaseUrl + "/ocr?maxPages=" + maxPages,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Resposta inválida do OCR service");
        }

        Object text = response.getBody().get("text");
        return text == null ? null : text.toString();
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(retryDelayMs);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }
}
