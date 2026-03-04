package br.com.dms.service;

import br.com.dms.audit.AuditActorResolver;
import br.com.dms.audit.AuditEventMessage;
import br.com.dms.audit.AuditEventPublisher;
import br.com.dms.repository.mongo.DmsDocumentRepository;
import br.com.dms.repository.mongo.DmsDocumentVersionRepository;
import br.com.dms.repository.redis.DocumentInformationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class DocumentDeleteService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentDeleteService.class);

    private final AmazonS3Service amazonS3Service;

    private final DocumentInformationRepository documentInformationRepository;

    private final DmsDocumentRepository dmsDocumentRepository;

    private final DmsDocumentVersionRepository dmsDocumentVersionRepository;
    private final AuditEventPublisher auditEventPublisher;
    private final AuditActorResolver auditActorResolver;

    public DocumentDeleteService(AmazonS3Service amazonS3Service,
                                 DocumentInformationRepository documentInformationRepository,
                                 DmsDocumentRepository dmsDocumentRepository,
                                 DmsDocumentVersionRepository dmsDocumentVersionRepository,
                                 AuditEventPublisher auditEventPublisher,
                                 AuditActorResolver auditActorResolver) {
        this.amazonS3Service = amazonS3Service;
        this.documentInformationRepository = documentInformationRepository;
        this.dmsDocumentRepository = dmsDocumentRepository;
        this.dmsDocumentVersionRepository = dmsDocumentVersionRepository;
        this.auditEventPublisher = auditEventPublisher;
        this.auditActorResolver = auditActorResolver;
    }

    public ResponseEntity<?> delete(String transactionId, String documentId) {
        var optEntity = dmsDocumentRepository.findById(documentId);

        optEntity.ifPresent(entity -> {
            logger.info("DMS - TransactionId: {} - Deletando documento do S3: {}", transactionId, documentId);
            amazonS3Service.deleteAllVersions(entity.getCpf(), entity.getFilename());
            logger.info("DMS - TransactionId: {} - Deletando documento do Mongo = {}", transactionId, documentId);
            dmsDocumentVersionRepository.deleteByDmsDocumentId(documentId);
            dmsDocumentRepository.deleteById(documentId);
            documentInformationRepository.delete(documentId, null);
            Map<String, Object> attributes = new java.util.HashMap<>();
            if (entity.getCategory() != null) {
                attributes.put("category", entity.getCategory());
            }
            auditEventPublisher.publish(new AuditEventMessage(
                    "DOCUMENT_DELETED",
                    Instant.now(),
                    auditActorResolver.resolveUserId(),
                    entity.getTenantId(),
                    "DOCUMENT",
                    documentId,
                    entity.getFilename(),
                    entity.getMetadata(),
                    attributes
            ));
        });

        return ResponseEntity.noContent().build();
    }
}
