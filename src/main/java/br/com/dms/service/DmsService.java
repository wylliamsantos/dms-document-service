package br.com.dms.service;

import br.com.dms.controller.request.FinalizeUploadRequest;
import br.com.dms.controller.request.PayloadApprove;
import br.com.dms.controller.request.PayloadUrlPresigned;
import br.com.dms.controller.response.UrlPresignedResponse;
import br.com.dms.domain.core.DocumentId;
import br.com.dms.domain.core.DocumentWorkflowStatus;
import br.com.dms.domain.mongodb.DmsDocument;
import br.com.dms.domain.mongodb.DmsDocumentVersion;
import br.com.dms.domain.mongodb.DocumentWorkflowTransition;
import br.com.dms.domain.core.VersionType;
import br.com.dms.domain.core.UploadStatus;
import br.com.dms.exception.DmsBusinessException;
import br.com.dms.exception.DmsException;
import br.com.dms.exception.TypeException;
import br.com.dms.repository.mongo.CategoryRepository;
import br.com.dms.repository.mongo.DmsDocumentRepository;
import br.com.dms.repository.mongo.DmsDocumentVersionRepository;
import br.com.dms.repository.mongo.DocumentWorkflowTransitionRepository;
import br.com.dms.repository.redis.DocumentInformationRepository;
import br.com.dms.service.signature.SigningService;
import br.com.dms.service.workflow.MetadataService;
import br.com.dms.util.DmsUtil;
import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.mime.MimeType;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static br.com.dms.domain.Messages.*;

@Service
@Slf4j
public class DmsService {

    private final AmazonS3Service amazonS3Service;

    private final DocumentInformationRepository documentInformationRepository;

    private final DmsDocumentRepository dmsDocumentRepository;

    private final DmsDocumentVersionRepository dmsDocumentVersionRepository;

    private final DocumentWorkflowTransitionRepository workflowTransitionRepository;

    private final CategoryRepository categoryRepository;

    private final DmsUtil dmsUtil;

    private final Environment environment;

    protected final SigningService signingService;

    private final MetadataService metadataService;
    private final DocumentValidationService validationService;

    public DmsService(AmazonS3Service amazonS3Service,
                      DocumentInformationRepository documentInformationRepository,
                      DmsDocumentRepository dmsDocumentRepository,
                      DmsDocumentVersionRepository dmsDocumentVersionRepository,
                      DocumentWorkflowTransitionRepository workflowTransitionRepository,
                      CategoryRepository categoryRepository,
                      DmsUtil dmsUtil,
                      Environment environment,
                      SigningService signingService,
                      MetadataService metadataService,
                      DocumentValidationService validationService) {
        this.amazonS3Service = amazonS3Service;
        this.documentInformationRepository = documentInformationRepository;
        this.dmsDocumentRepository = dmsDocumentRepository;
        this.dmsDocumentVersionRepository = dmsDocumentVersionRepository;
        this.workflowTransitionRepository = workflowTransitionRepository;
        this.categoryRepository = categoryRepository;
        this.dmsUtil = dmsUtil;
        this.environment = environment;
        this.signingService = signingService;
        this.metadataService = metadataService;
        this.validationService = validationService;
    }

    public ResponseEntity<?> reprove(String transactionId, String documentId, String documentVersion) {
        DmsDocument document = dmsDocumentRepository.findById(documentId)
            .orElseThrow(() -> new DmsBusinessException(String.format("Documento não encontrado para reprovação. Doc=%s", documentId), TypeException.VALID, transactionId));

        dmsDocumentVersionRepository.findByDmsDocumentIdAndVersionNumber(documentId, documentVersion)
            .orElseThrow(() -> new DmsBusinessException(String.format("Versão %s não encontrada para documento %s", documentVersion, documentId), TypeException.VALID, transactionId));

        transitionWorkflow(document, DocumentWorkflowStatus.REJECTED, "system", "Rejected by workflow action", transactionId);
        return ResponseEntity.noContent().build();
    }

    public ResponseEntity<?> updateMetadata(String transactionId, String documentId, String metadata, String fileName) {
        if (StringUtils.isBlank(metadata)) {
            throw new DmsBusinessException(environment.getProperty("dms.msg.metadataIsNull"), TypeException.VALID, transactionId);
        }

        Map<String, Object> jsonMetadata = dmsUtil.handleObject(transactionId, metadata);
        updateMetadata(transactionId, documentId, jsonMetadata, fileName);
        return ResponseEntity.noContent().build();
    }

    public void updateMetadata(String transactionId, String documentId, Map<String, Object> jsonMetadata, String fileName) {
        String businessKeyType = resolveBusinessKeyType(null);
        String businessKeyValue = dmsUtil.getBusinessKeyFromMetadata(jsonMetadata, businessKeyType);

        Optional<DmsDocument> optEntity = dmsDocumentRepository.findByBusinessKeyValueAndFilename(businessKeyValue, fileName);

        if (optEntity.isPresent()) {
            log.info("DMS - TransactionId: {} - Documento {} encontrado será atualizado", transactionId, documentId);
            var entity = optEntity.get();
            var dmsDocumentVersion = dmsDocumentVersionRepository.findLastVersionByDmsDocumentId(entity.getId()).orElseThrow();
            dmsDocumentVersion.setMetadata(jsonMetadata);
            dmsDocumentVersion.setModifiedAt(LocalDateTime.now());
            this.dmsDocumentVersionRepository.save(dmsDocumentVersion);
            entity.setMetadata(jsonMetadata);
            this.dmsDocumentRepository.save(entity);
        }
    }
    @Transactional
    public DocumentId createOrUpdate(String transactionId, boolean isFinal, MultipartFile document, LocalDate issuingDate, String author, String metadata,
                                     String documentCategoryName, String filename, String comment) {
        try {
            String effectiveFilename = StringUtils.isNotBlank(filename) ? filename : document.getOriginalFilename();
            byte[] documentBytes = document.getBytes();

            validationService.validateCategory(transactionId, documentCategoryName);
            validationService.validateFilename(transactionId, effectiveFilename);
            ByteArrayResource documentResource = new ByteArrayResource(documentBytes) {
                @Override
                public String getFilename() {
                    return effectiveFilename;
                }
            };

            validationService.validateAuthor(transactionId, author);
            ByteArrayInputStream documentData = new ByteArrayInputStream(documentBytes);

            return createOrUpdate(transactionId, isFinal, documentData, documentResource, issuingDate, author, metadata, documentCategoryName, effectiveFilename, comment);
        } catch (IOException e) {
            log.error("DMS - TransactionId: {} - Error processing multipart document", transactionId, e);
            throw new DmsException(environment.getProperty("dms.msg.unknowError"), TypeException.CONFIG, transactionId);
        }
    }

    @Transactional
    public DocumentId createOrUpdate(String transactionId, boolean isFinal, String documentAsBase64, LocalDate issuingDate, String author, String metadata, String documentCategoryName,
                                     String filenameDms, String comment) throws IOException {

        final ByteArrayInputStream documentData = new ByteArrayInputStream(Base64.decodeBase64(documentAsBase64));
        validationService.validateCategory(transactionId, documentCategoryName);
        validationService.validateFilename(transactionId, filenameDms);
        final ByteArrayResource documentResource = new ByteArrayResource(Base64.decodeBase64(documentAsBase64)) {
            @Override
            public String getFilename() {
                return filenameDms;
            }
        };

        validationService.validateAuthor(transactionId, author);
        return createOrUpdate(transactionId, isFinal, documentData, documentResource, issuingDate, author, metadata, documentCategoryName, filenameDms, comment);
    }

    @Transactional
    private DocumentId createOrUpdate(String transactionId, boolean isFinal, ByteArrayInputStream documentData, ByteArrayResource documentResource, LocalDate issuingDate, String author, String metadata, String documentCategoryName,
                                     String filenameDms, String comment) throws IOException {

        validationService.validateCategory(transactionId, documentCategoryName);
        validationService.validateFilename(transactionId, filenameDms);
        Map<String, Object> jsonMetadata = metadataService.getValideMetadata(transactionId, metadata, documentCategoryName, issuingDate);

        validationService.validateAuthor(transactionId, author);

        String businessKeyType = resolveBusinessKeyType(documentCategoryName);
        String businessKeyValue = dmsUtil.getBusinessKeyFromMetadata(jsonMetadata, businessKeyType);
        Optional<DmsDocument> optEntity = this.dmsDocumentRepository
                .findByBusinessKeyTypeAndBusinessKeyValueAndFilenameAndCategory(businessKeyType, businessKeyValue, filenameDms, documentCategoryName);

        final MimeType mimeType = this.dmsUtil.validateMimeType(transactionId, documentData);
        ByteArrayResource byteArrayResourceSignature = signingService.applyDigitalSignature(mimeType) ? signingService.signPdf(filenameDms, documentResource) : documentResource;
        ByteArrayInputStream inputStreamSignature = new ByteArrayInputStream(byteArrayResourceSignature.getByteArray());
        var entity = optEntity.orElseGet(() -> DmsDocument.of()
                .id(UUID.randomUUID().toString())
                .cpf(StringUtils.equalsIgnoreCase(businessKeyType, "cpf") ? businessKeyValue : null)
                .businessKeyType(businessKeyType)
                .businessKeyValue(businessKeyValue)
                .category(documentCategoryName)
                .mimeType(mimeType.getName())
                .filename(filenameDms)
                .workflowStatus(DocumentWorkflowStatus.DRAFT)
                .build());

        var maxDmsDocumentVersion = dmsDocumentVersionRepository.findLastVersionByDmsDocumentId(entity.getId());

        BigDecimal lastVersion = maxDmsDocumentVersion.map(DmsDocumentVersion::getVersionNumber).orElse(BigDecimal.ZERO);
        var version = dmsUtil.generateVersion(isFinal, lastVersion);
        var documentId = entity.getId();

        long contentLength = byteArrayResourceSignature.contentLength();
        String pathToNewDocument = amazonS3Service.createDocumentS3(filenameDms, businessKeyValue, version, inputStreamSignature, contentLength);

        LocalDateTime versionTimestamp = LocalDateTime.now();

        var newVersion = DmsDocumentVersion.of()
                .dmsDocumentId(entity.getId())
                .versionNumber(version)
                .versionType(isFinal ? VersionType.MAJOR : VersionType.MINOR)
                .creationDate(versionTimestamp)
                .modifiedAt(versionTimestamp)
                .pathToDocument(pathToNewDocument)
                .fileSize(contentLength)
                .metadata(jsonMetadata)
                .author(author)
                .comment(comment)
                .mimeType(mimeType.getName())
                .build();

        String idNewVersion = StringUtils.EMPTY;

        try {
            idNewVersion = dmsDocumentVersionRepository.save(newVersion).getId();
            entity.setMetadata(jsonMetadata);
            entity = dmsDocumentRepository.save(entity);
            transitionWorkflow(entity, DocumentWorkflowStatus.PENDING_REVIEW, author, "Document submitted for review", transactionId);
        } catch(DuplicateKeyException e) {
            log.error("Chave duplicada para criacao de documento filename={}, businessKey={}, version={}. Limpando registros orfãos", filenameDms, businessKeyValue, version);
            dmsDocumentVersionRepository.deleteById(idNewVersion);
            throw new DuplicateKeyException(String.format("Chave duplicada para criacao de documento filename=%s, businessKey=%s, version=%s. Registro ja se encontra na base. Verificar concorrencia nas chamadas", filenameDms, businessKeyValue, version));
        }

        documentInformationRepository.delete(documentId, null);
        return new DocumentId(documentId, String.valueOf(version));
    }


    public ResponseEntity<DocumentId> approveWithSignatureText(String documentId, String documentVersion, String transactionId, PayloadApprove payloadApprove) throws IOException {
        log.info("approveWithSignatureText documentId {} documentVersion {} transactionId {} signatureText {}", documentId, documentVersion, transactionId, payloadApprove);

        Optional<DmsDocument> optEntity = dmsDocumentRepository.findById(documentId);
        Optional<DmsDocumentVersion> optEntityVersion = dmsDocumentVersionRepository.findByDmsDocumentIdAndVersionNumber(documentId, documentVersion);

        DmsDocumentVersion currentDmsDocumentVersion = optEntityVersion.orElseThrow(() -> new DmsBusinessException(String.format("Documento e versão não encontrados para aprovação. Doc=%s, Versão=%s", documentId, documentVersion), TypeException.VALID));
        DmsDocument entity = optEntity.orElseThrow();

        if (currentDmsDocumentVersion.getFileSize() == 0) {
            String message = String.format("Documento para aprovação está corrompido. id=%s, version=%s, fileSize=%s", currentDmsDocumentVersion.getDmsDocumentId(), currentDmsDocumentVersion.getVersionNumber(), currentDmsDocumentVersion.getFileSize());
            throw new DmsBusinessException(message, TypeException.VALID, transactionId);
        }

        // Validar se o documento é PDF antes de tentar adicionar assinatura
        if (shouldAddSignatureText(payloadApprove)) {
            String mimeType = currentDmsDocumentVersion.getMimeType() != null ?
                currentDmsDocumentVersion.getMimeType() : entity.getMimeType();

            if (mimeType == null || !mimeType.equalsIgnoreCase("application/pdf")) {
                String message = String.format("Assinatura digital só pode ser aplicada em documentos PDF. Documento atual: id=%s, version=%s, mimeType=%s",
                    documentId, documentVersion, mimeType);
                throw new DmsBusinessException(message, TypeException.VALID, transactionId);
            }
        }

        DmsDocumentVersion lastDmsDocumentVersionMongo = dmsDocumentVersionRepository.findLastVersionByDmsDocumentId(documentId).get();
        BigDecimal lastVersion = lastDmsDocumentVersionMongo.getVersionNumber();
        BigDecimal newVersion = dmsUtil.generateNewMajorVersion(lastVersion);
        DmsDocumentVersion newVersionMongo = digitalSignatureAndSaveBucket(transactionId, payloadApprove, currentDmsDocumentVersion, entity, newVersion, lastDmsDocumentVersionMongo.getMimeType());

        dmsDocumentVersionRepository.save(newVersionMongo);
        entity.setMetadata(currentDmsDocumentVersion.getMetadata());
        entity = dmsDocumentRepository.save(entity);
        transitionWorkflow(entity, DocumentWorkflowStatus.APPROVED, "system", "Document approved", transactionId);

        DocumentId documentIdResponse = new DocumentId(documentId, newVersion.toPlainString());
        return new ResponseEntity<>(documentIdResponse, HttpStatus.OK);
    }

    public UrlPresignedResponse generatePresignedUrl(String transactionId, PayloadUrlPresigned payloadUrlPresigned) throws IOException {
        validationService.validateAuthor(transactionId, payloadUrlPresigned.getAuthor());
        validationService.validateCategory(transactionId, payloadUrlPresigned.getCategory());
        validationService.validateFilename(transactionId, payloadUrlPresigned.getFileName());
        Map<String, Object> metadata = metadataService.getValideMetadata(transactionId, payloadUrlPresigned.getMetadata(), payloadUrlPresigned.getCategory(), payloadUrlPresigned.getIssuingDate());

        String businessKeyType = resolveBusinessKeyType(payloadUrlPresigned.getCategory());
        String businessKeyValue = dmsUtil.getBusinessKeyFromMetadata(metadata, businessKeyType);
        Optional<DmsDocument> optEntity = this.dmsDocumentRepository
                .findByBusinessKeyTypeAndBusinessKeyValueAndFilenameAndCategory(businessKeyType, businessKeyValue, payloadUrlPresigned.getFileName(), payloadUrlPresigned.getCategory());

        var entity = optEntity.orElseGet(() -> DmsDocument.of()
                .id(UUID.randomUUID().toString())
                .cpf(StringUtils.equalsIgnoreCase(businessKeyType, "cpf") ? businessKeyValue : null)
                .businessKeyType(businessKeyType)
                .businessKeyValue(businessKeyValue)
                .category(payloadUrlPresigned.getCategory())
                .mimeType(payloadUrlPresigned.getMimeType())// todo validar o que colocar pois o base64 não é passado
                .filename(payloadUrlPresigned.getFileName())
                .workflowStatus(DocumentWorkflowStatus.DRAFT)
                .build());

        var maxDmsDocumentVersion = dmsDocumentVersionRepository.findLastVersionByDmsDocumentId(entity.getId());

        BigDecimal lastVersion = maxDmsDocumentVersion.map(DmsDocumentVersion::getVersionNumber).orElse(BigDecimal.ZERO);
        var version = dmsUtil.generateVersion(payloadUrlPresigned.isFinal(), lastVersion);
        var documentId = entity.getId();

        String pathToNewDocument = amazonS3Service.getPathToDocument(payloadUrlPresigned.getFileName(), businessKeyValue, String.valueOf(version));

        LocalDateTime presignedTimestamp = LocalDateTime.now();

        var newVersion = DmsDocumentVersion.of()
                .dmsDocumentId(documentId)
                .versionNumber(version)
                .versionType(payloadUrlPresigned.isFinal() ? VersionType.MAJOR : VersionType.MINOR)
                .creationDate(presignedTimestamp)
                .modifiedAt(presignedTimestamp)
                .pathToDocument(pathToNewDocument)
                .fileSize(payloadUrlPresigned.getFileSize()) //todo validar o que colocar pois o base64 não é passado
                .metadata(metadata)
                .author(payloadUrlPresigned.getAuthor())
                .comment(payloadUrlPresigned.getComment())
                .mimeType(payloadUrlPresigned.getMimeType())
                .uploadStatus(UploadStatus.PENDING)
                .build();

        String idNewVersion = StringUtils.EMPTY;

        try {
            idNewVersion = dmsDocumentVersionRepository.save(newVersion).getId();
            entity.setMetadata(metadata);
            entity.setMimeType(payloadUrlPresigned.getMimeType());
            entity.setFilename(payloadUrlPresigned.getFileName());
            entity = dmsDocumentRepository.save(entity);
            transitionWorkflow(entity, DocumentWorkflowStatus.PENDING_REVIEW, payloadUrlPresigned.getAuthor(), "Presigned upload requested", transactionId);
        } catch(DuplicateKeyException e) {
            log.error("Chave duplicada para criacao de documento filename={}, businessKey={}, version={}. Limpando registros orfãos", payloadUrlPresigned.getFileName(), businessKeyValue, version);
            dmsDocumentVersionRepository.deleteById(idNewVersion);
            throw new DuplicateKeyException(String.format("Chave duplicada para criacao de documento filename=%s, businessKey=%s, version=%s. Registro ja se encontra na base. Verificar concorrencia nas chamadas", payloadUrlPresigned.getFileName(), businessKeyValue, version));
        }

        //Retira do cache esse id
        documentInformationRepository.delete(documentId, null);

        // Criando metadados do objeto
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.addUserMetadata("documentId", documentId);

        // Criando a URL pré-assinada
        long expirationInMinutes = Long.parseLong(environment.getProperty("aws.s3.expiration"));
        Date expiration = new Date();
        expiration.setTime(System.currentTimeMillis() + (expirationInMinutes * 60 * 1000));

        GeneratePresignedUrlRequest generatePresignedUrlRequest = new GeneratePresignedUrlRequest(amazonS3Service.getBucketName(), newVersion.getPathToDocument())
                .withMethod(HttpMethod.PUT)
                .withExpiration(expiration);

        URL presignedUrl = amazonS3Service.generatePresignedUrl(generatePresignedUrlRequest);

        return UrlPresignedResponse.builder()
                .url(presignedUrl)
                .id(new DocumentId(documentId, String.valueOf(version)))
                .build();

    }

    public DocumentId finalizeUpload(String transactionId, String documentId, FinalizeUploadRequest request) {
        var versionOpt = dmsDocumentVersionRepository.findByDmsDocumentIdAndVersionNumber(documentId, request.getVersion());

        if (versionOpt.isEmpty()) {
            throw new DmsBusinessException(DOCUMENT_VERSION_NOT_FOUND, TypeException.VALID, transactionId);
        }

        var version = versionOpt.get();

        if (UploadStatus.COMPLETED.equals(version.getUploadStatus())) {
            return new DocumentId(documentId, version.getVersionNumber().toPlainString());
        }

        if (StringUtils.isBlank(version.getPathToDocument())) {
            throw new DmsBusinessException(DOCUMENT_UPLOAD_PATH_MISSING, TypeException.VALID, transactionId);
        }

        if (!amazonS3Service.objectExists(version.getPathToDocument())) {
            throw new DmsBusinessException(DOCUMENT_UPLOAD_NOT_FOUND, TypeException.VALID, transactionId);
        }

        var objectMetadata = amazonS3Service.getObjectMetadata(version.getPathToDocument());
        long objectSize = objectMetadata.getContentLength();

        if (request.getFileSize() != null && !request.getFileSize().equals(objectSize)) {
            throw new DmsBusinessException(DOCUMENT_UPLOAD_SIZE_MISMATCH, TypeException.VALID, transactionId);
        }

        version.setFileSize(objectSize);
        version.setUploadStatus(UploadStatus.COMPLETED);
        version.setModifiedAt(LocalDateTime.now());

        if (StringUtils.isNotBlank(request.getMimeType())) {
            version.setMimeType(request.getMimeType());
        }

        dmsDocumentVersionRepository.save(version);

        dmsDocumentRepository.findById(documentId).ifPresent(document -> {
            if (StringUtils.isNotBlank(request.getMimeType())) {
                document.setMimeType(request.getMimeType());
            }
            dmsDocumentRepository.save(document);
        });

        documentInformationRepository.delete(documentId, null);
        documentInformationRepository.delete(documentId, version.getVersionNumber().toPlainString());

        return new DocumentId(documentId, version.getVersionNumber().toPlainString());
    }

    private void transitionWorkflow(DmsDocument document,
                                    DocumentWorkflowStatus targetStatus,
                                    String actor,
                                    String reason,
                                    String transactionId) {
        DocumentWorkflowStatus currentStatus = document.getWorkflowStatus() == null
            ? DocumentWorkflowStatus.DRAFT
            : document.getWorkflowStatus();

        if (currentStatus == targetStatus) {
            return;
        }

        boolean valid = isValidTransition(currentStatus, targetStatus);
        if (!valid) {
            throw new DmsBusinessException(
                String.format("Transição de workflow inválida para doc=%s: %s -> %s", document.getId(), currentStatus, targetStatus),
                TypeException.VALID,
                transactionId
            );
        }

        document.setWorkflowStatus(targetStatus);
        dmsDocumentRepository.save(document);

        workflowTransitionRepository.save(
            DocumentWorkflowTransition.of()
                .documentId(document.getId())
                .fromStatus(currentStatus)
                .toStatus(targetStatus)
                .actor(StringUtils.defaultIfBlank(actor, "system"))
                .reason(StringUtils.defaultIfBlank(reason, "workflow transition"))
                .changedAt(LocalDateTime.now())
                .build()
        );
    }

    private boolean isValidTransition(DocumentWorkflowStatus currentStatus, DocumentWorkflowStatus targetStatus) {
        return switch (currentStatus) {
            case DRAFT -> targetStatus == DocumentWorkflowStatus.PENDING_REVIEW
                || targetStatus == DocumentWorkflowStatus.APPROVED
                || targetStatus == DocumentWorkflowStatus.REJECTED;
            case PENDING_REVIEW -> targetStatus == DocumentWorkflowStatus.APPROVED || targetStatus == DocumentWorkflowStatus.REJECTED;
            case APPROVED -> targetStatus == DocumentWorkflowStatus.PENDING_REVIEW;
            case REJECTED -> targetStatus == DocumentWorkflowStatus.PENDING_REVIEW;
        };
    }

    public DocumentWorkflowStatus reviewDocumentWorkflow(String transactionId,
                                                         String documentId,
                                                         String action,
                                                         String reason,
                                                         String actor) {
        DmsDocument document = dmsDocumentRepository.findById(documentId)
            .orElseThrow(() -> new DmsBusinessException(String.format("Documento não encontrado para revisão. Doc=%s", documentId), TypeException.VALID, transactionId));

        String normalizedAction = StringUtils.trimToEmpty(action).toUpperCase();
        switch (normalizedAction) {
            case "APPROVE" -> transitionWorkflow(document, DocumentWorkflowStatus.APPROVED, actor, "Approved by workflow review", transactionId);
            case "REPROVE" -> {
                if (StringUtils.isBlank(reason)) {
                    throw new DmsBusinessException("Motivo é obrigatório para reprovação", TypeException.VALID, transactionId);
                }
                transitionWorkflow(document, DocumentWorkflowStatus.REJECTED, actor, reason, transactionId);
            }
            default -> throw new DmsBusinessException("Ação de revisão inválida. Use APPROVE ou REPROVE", TypeException.VALID, transactionId);
        }

        return dmsDocumentRepository.findById(documentId)
            .map(DmsDocument::getWorkflowStatus)
            .orElse(DocumentWorkflowStatus.DRAFT);
    }

    private String resolveBusinessKeyType(String categoryName) {
        if (StringUtils.isBlank(categoryName)) {
            return "cpf";
        }

        return categoryRepository.findByName(categoryName)
            .map(category -> StringUtils.defaultIfBlank(category.getUniqueAttributes(), "cpf"))
            .map(attributes -> attributes.split(",")[0].trim())
            .filter(StringUtils::isNotBlank)
            .orElse("cpf");
    }

    private String getStorageKeySegment(DmsDocument document) {
        if (StringUtils.isNotBlank(document.getBusinessKeyValue())) {
            return document.getBusinessKeyValue();
        }
        return document.getCpf();
    }

    private DmsDocumentVersion digitalSignatureAndSaveBucket(String transactionId, PayloadApprove payloadApprove, DmsDocumentVersion currentDmsDocumentVersion, DmsDocument entity, BigDecimal newVersion, String mimeType) throws IOException {
        String pathToDocument;
        Long fileSize;

        if (shouldAddSignatureText(payloadApprove)) {
            byte[] documentContent;
            if (StringUtils.isEmpty(currentDmsDocumentVersion.getPathToDocument())) {
                documentContent = amazonS3Service.getDocumentContentFromS3(entity.getFilename(), getStorageKeySegment(entity), String.valueOf(currentDmsDocumentVersion.getVersionNumber()));
            } else {
                documentContent = amazonS3Service.getDocumentContentFromS3(currentDmsDocumentVersion.getPathToDocument());
            }

            ByteArrayResource documentWithDigitalSignature = signDocument(documentContent, transactionId, payloadApprove.getSignatureText(), entity.getFilename());
            ByteArrayInputStream documentData = new ByteArrayInputStream(documentWithDigitalSignature.getInputStream().readAllBytes());
            fileSize = documentWithDigitalSignature.contentLength();
            pathToDocument = amazonS3Service.createDocumentS3(entity.getFilename(), getStorageKeySegment(entity), newVersion, documentData, fileSize);
        } else {
            pathToDocument = amazonS3Service.copyDocumentS3(entity.getFilename(), getStorageKeySegment(entity), newVersion, currentDmsDocumentVersion.getVersionNumber());
            fileSize = currentDmsDocumentVersion.getFileSize();
        }

        return DmsDocumentVersion.of()
                .dmsDocumentId(entity.getId())
                .versionNumber(newVersion)
                .versionType(VersionType.MAJOR)
                .creationDate(currentDmsDocumentVersion.getCreationDate())
                .modifiedAt(LocalDateTime.now())
                .fileSize(fileSize)
                .pathToDocument(pathToDocument)
                .author(currentDmsDocumentVersion.getAuthor())
                .metadata(currentDmsDocumentVersion.getMetadata())
                .mimeType(mimeType)
                .uploadStatus(UploadStatus.COMPLETED)
                .build();
    }

    private boolean shouldAddSignatureText(PayloadApprove payloadApprove) {
        return payloadApprove != null && StringUtils.isNotBlank(payloadApprove.getSignatureText());
    }

    private ByteArrayResource signDocument(byte[] documentContent, String transactionId, String signatureText, String fileName) throws IOException {
        ByteArrayInputStream documentData = new ByteArrayInputStream(documentContent);
        ByteArrayResource signedPdf = signingService.signSignaturePdf(documentContent, signatureText);
        MimeType mimeType = dmsUtil.validateMimeType(transactionId, documentData);
        return signingService.applyDigitalSignature(mimeType) ? signingService.signPdf(fileName, signedPdf) : signedPdf;
    }


}
