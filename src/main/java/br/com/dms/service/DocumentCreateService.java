package br.com.dms.service;

import br.com.dms.domain.core.DocumentCategory;
import br.com.dms.domain.core.DocumentId;
import br.com.dms.domain.mongodb.Category;
import br.com.dms.domain.mongodb.DmsDocument;
import br.com.dms.domain.mongodb.DmsDocumentVersion;
import br.com.dms.domain.core.VersionType;
import br.com.dms.exception.DmsException;
import br.com.dms.exception.TypeException;
import br.com.dms.repository.mongo.DmsDocumentRepository;
import br.com.dms.repository.mongo.DmsDocumentVersionRepository;
import br.com.dms.repository.redis.DocumentInformationRepository;
import br.com.dms.service.handler.DocumentCategoryHandler;
import br.com.dms.service.handler.IssuingDateHandler;
import br.com.dms.service.workflow.MetadataService;
import br.com.dms.service.signature.SigningService;
import br.com.dms.util.DmsUtil;
import lombok.ToString;
import lombok.With;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.mime.MimeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class DocumentCreateService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentCreateService.class);

    private final AmazonS3Service amazonS3Service;
    private final DocumentInformationRepository documentInformationRepository;
    private final DmsUtil dmsUtil;
    private final DmsDocumentRepository dmsDocumentRepository;
    private final DmsDocumentVersionRepository dmsDocumentVersionRepository;
    private final Environment environment;
    private final SigningService signingService;
    private final CategoryService categoryService;
    private final DocumentCategoryHandler documentCategoryHandler;
    private final IssuingDateHandler issuingDateHandler;
    private final MetadataService metadataService;

    public DocumentCreateService(AmazonS3Service amazonS3Service,
                                 DocumentInformationRepository documentInformationRepository,
                                 DmsUtil dmsUtil,
                                 DmsDocumentRepository dmsDocumentRepository,
                                 DmsDocumentVersionRepository dmsDocumentVersionRepository,
                                 Environment environment,
                                 SigningService signingService,
                                 CategoryService categoryService,
                                 DocumentCategoryHandler documentCategoryHandler,
                                 IssuingDateHandler issuingDateHandler,
                                 MetadataService metadataService) {
        this.amazonS3Service = amazonS3Service;
        this.documentInformationRepository = documentInformationRepository;
        this.dmsUtil = dmsUtil;
        this.dmsDocumentRepository = dmsDocumentRepository;
        this.dmsDocumentVersionRepository = dmsDocumentVersionRepository;
        this.environment = environment;
        this.signingService = signingService;
        this.categoryService = categoryService;
        this.documentCategoryHandler = documentCategoryHandler;
        this.issuingDateHandler = issuingDateHandler;
        this.metadataService = metadataService;
    }

    public ResponseEntity<DocumentId> createWithMultipart(String transactionId,
                                                 String comment,
                                                 String category,
                                                 LocalDate issuingDate,
                                                 String jsonMetadata,
                                                 String filename,
                                                 String author,
                                                 MultipartFile document) {

        try {
            final String filenameDms = StringUtils.isNotBlank(filename) ? filename : document.getOriginalFilename();
            final byte[] documentBytes = document.getBytes();

            ByteArrayResource documentResource = new ByteArrayResource(documentBytes) {
                @Override
                public String getFilename() {
                    return filenameDms;
                }
            };

            ByteArrayInputStream documentData = new ByteArrayInputStream(documentBytes);
            Map<String, Object> metadata = metadataService.getValideMetadata(transactionId, jsonMetadata, category, issuingDate);

            return validateAndCreate(transactionId, true, issuingDate, author, metadata, category, filenameDms, comment, documentData, documentResource);
        } catch (IOException e) {
            logger.error("DMS - TransactionId: {} - Error creating document from multipart payload", transactionId, e);
            throw new DmsException(environment.getProperty("dms.msg.unknowError"), TypeException.CONFIG, transactionId);
        }
    }

    public ResponseEntity<DocumentId> createWithBase64(String transactionId, boolean isFinal, LocalDate issuingDate, String author, Map<String, Object> metadados,
                                              String categoryName, String filenameDms, String comment, String documentBase64) {

        byte[] documentBytes = dmsUtil.decodeBase64(transactionId, documentBase64);

        ByteArrayResource documentResource = new ByteArrayResource(documentBytes) {
            @Override
            public String getFilename() {
                return filenameDms;
            }
        };

        ByteArrayInputStream documentData = new ByteArrayInputStream(documentBytes);

        Map<String, Object> metadata = metadataService.getValideMetadata(transactionId, metadados, categoryName, issuingDate);

        return validateAndCreate(transactionId, isFinal, issuingDate, author, metadata, categoryName, filenameDms, comment, documentData, documentResource);
    }

    private ResponseEntity<DocumentId> validateAndCreate(String transactionId, boolean isFinal, LocalDate issuingDate, String author, Map<String, Object> metadata, String categoryName, String filenameDms,
                                               String comment, ByteArrayInputStream documentData, ByteArrayResource documentResource) {
        DocumentCategory documentCategory = documentCategoryHandler.resolveCategory(transactionId, categoryName, metadata);
        issuingDateHandler.handle(transactionId, issuingDate, documentCategory, metadata);

        return create(transactionId, isFinal, author, categoryName, filenameDms, comment, documentData, documentResource, documentCategory, metadata);
    }

    private ResponseEntity<DocumentId> create(String transactionId, boolean isFinal, String author, String categoryName, String filenameDms,
                                    String comment, ByteArrayInputStream documentData, ByteArrayResource documentResource, DocumentCategory documentCategory, Map<String, Object> metadata) {

        Category category = categoryService.getCategoryByName(categoryName);
        var cpfKey = dmsUtil.getCpfKeyRequired(category);
        String cpf = dmsUtil.getCpfFromMetadata(metadata, cpfKey);

        var existingDocumentOpt = dmsDocumentRepository.findByCpfAndFilenameAndCategory(cpf, filenameDms, categoryName);

        final MimeType mimeType = this.dmsUtil.validateMimeType(transactionId, documentData);
        ByteArrayResource byteArrayResourceSignature = signingService.applyDigitalSignature(mimeType) ? signingService.signPdf(filenameDms, documentResource) : documentResource;
        byte[] signedBytes = byteArrayResourceSignature.getByteArray();
        long contentLength = byteArrayResourceSignature.contentLength();

        if (existingDocumentOpt.isPresent()) {
            return createNewVersion(isFinal, author, comment, metadata, mimeType,
                    existingDocumentOpt.get(), filenameDms, signedBytes, contentLength);
        }

        BigDecimal initialVersion = isFinal ? new BigDecimal("1.0") : new BigDecimal("0.1");
        VersionType initialVersionType = isFinal ? VersionType.MAJOR : VersionType.MINOR;

        var newDocument = DmsDocument.of()
                .id(UUID.randomUUID().toString())
                .filename(filenameDms)
                .category(documentCategory.getName())
                .cpf(cpf)
                .metadata(metadata)
                .mimeType(mimeType.getName())
                .build();

        String pathToDocument = amazonS3Service.createDocumentS3(filenameDms, cpf, initialVersion, new ByteArrayInputStream(signedBytes), contentLength);

        LocalDateTime now = LocalDateTime.now();

        var newVersion = DmsDocumentVersion.of()
                .dmsDocumentId(newDocument.getId())
                .versionNumber(initialVersion)
                .versionType(initialVersionType)
                .creationDate(now)
                .modifiedAt(now)
                .fileSize(contentLength)
                .author(author)
                .pathToDocument(pathToDocument)
                .metadata(metadata)
                .comment(comment)
                .mimeType(mimeType.getName())
                .build();

        dmsDocumentVersionRepository.save(newVersion);
        dmsDocumentRepository.save(newDocument);
        DocumentId documentId = new DocumentId(newDocument.getId(), initialVersion.toPlainString());
        return ResponseEntity.status(HttpStatus.CREATED).body(documentId);
    }

    private ResponseEntity<DocumentId> createNewVersion(boolean isFinal,
                                                        String author,
                                                        String comment,
                                                        Map<String, Object> metadata,
                                                        MimeType mimeType,
                                                        DmsDocument existingDocument,
                                                        String filenameDms,
                                                        byte[] signedBytes,
                                                        long contentLength) {
        //limpa o cache do documento
        documentInformationRepository.delete(existingDocument.getId(), null);

        var lastVersionOpt = dmsDocumentVersionRepository.findLastVersionByDmsDocumentId(existingDocument.getId());
        BigDecimal lastVersion = lastVersionOpt.map(DmsDocumentVersion::getVersionNumber).orElse(new BigDecimal("0.0"));
        BigDecimal nextVersion = dmsUtil.generateVersion(isFinal, lastVersion);
        VersionType versionType = isFinal ? VersionType.MAJOR : VersionType.MINOR;

        existingDocument.setMetadata(metadata);
        existingDocument.setMimeType(mimeType.getName());
        existingDocument.setFilename(filenameDms);

        String pathToDocument = amazonS3Service.createDocumentS3(filenameDms, existingDocument.getCpf(), nextVersion,
                new ByteArrayInputStream(signedBytes), contentLength);

        LocalDateTime nowVersion = LocalDateTime.now();

        var newDocumentVersion = DmsDocumentVersion.of()
                .dmsDocumentId(existingDocument.getId())
                .versionNumber(nextVersion)
                .versionType(versionType)
                .creationDate(nowVersion)
                .modifiedAt(nowVersion)
                .fileSize(contentLength)
                .author(author)
                .comment(comment)
                .metadata(metadata)
                .mimeType(mimeType.getName())
                .pathToDocument(pathToDocument)
                .build();

        dmsDocumentVersionRepository.save(newDocumentVersion);
        dmsDocumentRepository.save(existingDocument);

        DocumentId documentId = new DocumentId(existingDocument.getId(), nextVersion.toPlainString());

        return ResponseEntity.status(HttpStatus.CREATED).body(documentId);
    }
}
