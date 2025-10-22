package br.com.dms.service;

import br.com.dms.repository.mongo.DmsDocumentRepository;
import br.com.dms.repository.mongo.DmsDocumentVersionRepository;
import br.com.dms.repository.redis.DocumentInformationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class DocumentDeleteService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentDeleteService.class);

    private final AmazonS3Service amazonS3Service;

    private final DocumentInformationRepository documentInformationRepository;

    private final DmsDocumentRepository dmsDocumentRepository;

    private final DmsDocumentVersionRepository dmsDocumentVersionRepository;

    public DocumentDeleteService(AmazonS3Service amazonS3Service,
                                 DocumentInformationRepository documentInformationRepository,
                                 DmsDocumentRepository dmsDocumentRepository,
                                 DmsDocumentVersionRepository dmsDocumentVersionRepository) {
        this.amazonS3Service = amazonS3Service;
        this.documentInformationRepository = documentInformationRepository;
        this.dmsDocumentRepository = dmsDocumentRepository;
        this.dmsDocumentVersionRepository = dmsDocumentVersionRepository;
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
        });

        return ResponseEntity.noContent().build();
    }
}
