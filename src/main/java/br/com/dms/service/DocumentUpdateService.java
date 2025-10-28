package br.com.dms.service;

import br.com.dms.domain.core.DocumentId;
import br.com.dms.domain.mongodb.DmsDocumentVersion;
import br.com.dms.domain.core.VersionType;
import br.com.dms.exception.DmsException;
import br.com.dms.exception.TypeException;
import br.com.dms.repository.mongo.DmsDocumentRepository;
import br.com.dms.repository.mongo.DmsDocumentVersionRepository;
import br.com.dms.repository.redis.DocumentInformationRepository;
import br.com.dms.service.signature.SigningService;
import br.com.dms.util.DmsUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.mime.MimeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class DocumentUpdateService {

    private static Logger logger = LoggerFactory.getLogger(DocumentUpdateService.class);

    private final AmazonS3Service amazonS3Service;


    protected final Environment environment;

    private final DocumentInformationRepository documentInformationRepository;

    private final DmsDocumentRepository dmsDocumentRepository;

    private final DmsDocumentVersionRepository dmsDocumentVersionRepository;

    protected final DmsUtil dmsUtil;

    protected final SigningService signingService;

    public DocumentUpdateService(AmazonS3Service amazonS3Service,
                                 Environment environment,
                                 DocumentInformationRepository documentInformationRepository,
                                 DmsDocumentRepository dmsDocumentRepository,
                                 DmsDocumentVersionRepository dmsDocumentVersionRepository,
                                 DmsUtil dmsUtil,
                                 SigningService signingService) {
        this.amazonS3Service = amazonS3Service;
        this.environment = environment;
        this.documentInformationRepository = documentInformationRepository;
        this.dmsDocumentRepository = dmsDocumentRepository;
        this.dmsDocumentVersionRepository = dmsDocumentVersionRepository;
        this.dmsUtil = dmsUtil;
        this.signingService = signingService;
    }

    public ResponseEntity<DocumentId> updateWithMultipart(String documentId,
                                                 String transactionId,
                                                 boolean isFinal,
                                                 String metadata,
                                                 LocalDate issuingDate,
                                                 String author,
                                                 String filename,
                                                 String comment,
                                                 MultipartFile document) {

        try {
            final String filenameDms = StringUtils.isNotBlank(filename) ? filename : document.getOriginalFilename();
            byte[] documentBytes = document.getBytes();
            ByteArrayInputStream documentData = new ByteArrayInputStream(documentBytes);
            ByteArrayResource documentResource = new ByteArrayResource(documentBytes);

            return update(documentId, transactionId, isFinal, metadata, issuingDate, author, filenameDms, comment, documentData, documentResource);
        } catch (IOException e) {
            logger.error("DMS - TransactionId: {} - Error updating document from multipart payload", transactionId, e);
            throw new DmsException(environment.getProperty("dms.msg.unknowError"), TypeException.CONFIG, transactionId);
        }
    }

    public ResponseEntity<DocumentId> updateWithBase64(String documentId, String transactionId, boolean isFinal, String metadata, LocalDate issuingDate,
                                              String author, String filenameDms, String comment, String documentBase64) {

        byte[] documentBytes = dmsUtil.decodeBase64(transactionId, documentBase64);
        ByteArrayInputStream documentData = new ByteArrayInputStream(documentBytes);
        ByteArrayResource documentResource = new ByteArrayResource(documentBytes);

        return update(documentId, transactionId, isFinal, metadata, issuingDate, author, filenameDms, comment, documentData, documentResource);
    }

    public ResponseEntity<DocumentId> updateWithBase64(String documentId, String transactionId, boolean isFinal, Map<String, Object> metadados, LocalDate issuingDate,
                                              String author, String filenameDms, String comment, String documentBase64) {

        byte[] documentBytes = dmsUtil.decodeBase64(transactionId, documentBase64);
        ByteArrayInputStream documentData = new ByteArrayInputStream(documentBytes);
        ByteArrayResource documentResource = new ByteArrayResource(documentBytes);

        return update(documentId, transactionId, isFinal, metadados, issuingDate, author, filenameDms, comment, documentData, documentResource);
    }

    public ResponseEntity<DocumentId> update(String documentId, String transactionId, boolean isFinal, String metadata, LocalDate issuingDate,
                                    String author, String filenameDms, String comment, ByteArrayInputStream documentData, ByteArrayResource documentResource) {
        documentInformationRepository.delete(documentId, null);
        var optEntity = dmsDocumentRepository.findById(documentId);

        if (!optEntity.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        var entity = optEntity.get();
        var lastVersionMigrated = dmsDocumentVersionRepository.findLastVersionByDmsDocumentId(entity.getId()).get();
        var newVersion = dmsUtil.generateVersion(isFinal, lastVersionMigrated.getVersionNumber());
        VersionType versionType = isFinal ? VersionType.MAJOR : VersionType.MINOR;

        var mapProperties = dmsUtil.handleObject(transactionId, metadata);

        String cpf = dmsUtil.getCpfFromMetadata(mapProperties);

        final MimeType mimeType = this.dmsUtil.validateMimeType(transactionId, documentData);
        ByteArrayResource byteArrayResourceSignature = signingService.applyDigitalSignature(mimeType) ? signingService.signPdf(filenameDms, documentResource) : documentResource;
        ByteArrayInputStream inputStreamSignature = new ByteArrayInputStream(byteArrayResourceSignature.getByteArray());
        long contentLength = byteArrayResourceSignature.contentLength();

        String pathToDocument = amazonS3Service.createDocumentS3(filenameDms, cpf, newVersion, inputStreamSignature, contentLength);

        var entityNewVersion = DmsDocumentVersion.of()
                .dmsDocumentId(entity.getId())
                .versionNumber(newVersion)
                .versionType(versionType)
                .creationDate(LocalDateTime.now())
                .modifiedAt(LocalDateTime.now())
                .author(author)
                .comment(comment)
                .fileSize(contentLength)
                .pathToDocument(pathToDocument)
                .metadata(mapProperties)
                .build();

        dmsDocumentVersionRepository.save(entityNewVersion);
        entity.setMetadata(mapProperties);
        dmsDocumentRepository.save(entity);

        DocumentId documentIdResponse = new DocumentId(entity.getId(), newVersion.toPlainString());
        return ResponseEntity.ok(documentIdResponse);
    }

    public ResponseEntity<DocumentId> update(String documentId, String transactionId, boolean isFinal, Map<String, Object> metadados, LocalDate issuingDate,
                                    String author, String filenameDms, String comment, ByteArrayInputStream documentData, ByteArrayResource documentResource) {
        documentInformationRepository.delete(documentId, null);
        var optEntity = dmsDocumentRepository.findById(documentId);

        if (optEntity.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        var entity = optEntity.get();
        var lastVersionMigrated = dmsDocumentVersionRepository.findLastVersionByDmsDocumentId(entity.getId()).get();
        var newVersion = dmsUtil.generateVersion(isFinal, lastVersionMigrated.getVersionNumber());
        VersionType versionType = isFinal ? VersionType.MAJOR : VersionType.MINOR;

        var mapProperties = dmsUtil.handleObject(metadados);

        String cpf = dmsUtil.getCpfFromMetadata(mapProperties);

        final MimeType mimeType = this.dmsUtil.validateMimeType(transactionId, documentData);
        ByteArrayResource byteArrayResourceSignature = signingService.applyDigitalSignature(mimeType) ? signingService.signPdf(filenameDms, documentResource) : documentResource;
        ByteArrayInputStream inputStreamSignature = new ByteArrayInputStream(byteArrayResourceSignature.getByteArray());

        long contentLength = byteArrayResourceSignature.contentLength();

        String pathToDocument = amazonS3Service.createDocumentS3(filenameDms, cpf, newVersion, inputStreamSignature, contentLength);

        var entityNewVersion = DmsDocumentVersion.of()
                .dmsDocumentId(entity.getId())
                .versionNumber(newVersion)
                .versionType(versionType)
                .creationDate(LocalDateTime.now())
                .modifiedAt(LocalDateTime.now())
                .author(author)
                .comment(comment)
                .fileSize(contentLength)
                .pathToDocument(pathToDocument)
                .metadata(mapProperties)
                .build();

        dmsDocumentVersionRepository.save(entityNewVersion);
        entity.setMetadata(mapProperties);
        dmsDocumentRepository.save(entity);

        DocumentId documentIdResponse = new DocumentId(entity.getId(), newVersion.toPlainString());
        return ResponseEntity.ok(documentIdResponse);
    }

    public ResponseEntity<?> updateMetadata(String transactionId, String documentId, HashMap<String, Object> jsonMetadata) {
        logger.debug("DMS - TransactionId: {} - Update metadata - documentId: {} - jsonMetadata: {}", transactionId, documentId, jsonMetadata);
        documentInformationRepository.delete(documentId, null);

        var optEntity = dmsDocumentRepository.findById(documentId);

        if (!optEntity.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        var entity = optEntity.get();
        var lastVersion = dmsDocumentVersionRepository.findLastVersionByDmsDocumentId(entity.getId()).get();

        lastVersion.setMetadata(jsonMetadata);
        lastVersion.setModifiedAt(LocalDateTime.now());
        entity.setMetadata(jsonMetadata);

        documentInformationRepository.delete(documentId, null);
        dmsDocumentVersionRepository.save(lastVersion);
        dmsDocumentRepository.save(entity);

        return ResponseEntity.noContent().build();
    }

}
