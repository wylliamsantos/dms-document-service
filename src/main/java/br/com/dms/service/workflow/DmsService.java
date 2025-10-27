package br.com.dms.service.workflow;

import br.com.dms.controller.request.PayloadApprove;
import br.com.dms.controller.request.PayloadUrlPresigned;
import br.com.dms.controller.response.UrlPresignedResponse;
import br.com.dms.domain.core.DocumentId;
import br.com.dms.domain.mongodb.DmsDocument;
import br.com.dms.domain.mongodb.DmsDocumentVersion;
import br.com.dms.domain.mongodb.type.VersionType;
import br.com.dms.exception.DmsBusinessException;
import br.com.dms.exception.DmsException;
import br.com.dms.exception.TypeException;
import br.com.dms.repository.mongo.DmsDocumentRepository;
import br.com.dms.repository.mongo.DmsDocumentVersionRepository;
import br.com.dms.repository.redis.DocumentInformationRepository;
import br.com.dms.service.AmazonS3Service;
import br.com.dms.service.signature.SigningService;
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

@Service
@Slf4j
public class DmsService {

    private final AmazonS3Service amazonS3Service;

    private final DocumentInformationRepository documentInformationRepository;

    private final DmsDocumentRepository dmsDocumentRepository;

    private final DmsDocumentVersionRepository dmsDocumentVersionRepository;

    private final DmsUtil dmsUtil;

    private final Environment environment;

    protected final SigningService signingService;

    private final MetadataService metadataService;

    public DmsService(AmazonS3Service amazonS3Service,
                      DocumentInformationRepository documentInformationRepository,
                      DmsDocumentRepository dmsDocumentRepository,
                      DmsDocumentVersionRepository dmsDocumentVersionRepository,
                      DmsUtil dmsUtil,
                      Environment environment,
                      SigningService signingService,
                      MetadataService metadataService) {
        this.amazonS3Service = amazonS3Service;
        this.documentInformationRepository = documentInformationRepository;
        this.dmsDocumentRepository = dmsDocumentRepository;
        this.dmsDocumentVersionRepository = dmsDocumentVersionRepository;
        this.dmsUtil = dmsUtil;
        this.environment = environment;
        this.signingService = signingService;
        this.metadataService = metadataService;
    }

    public ResponseEntity<?> reprove(String transactionId, String documentId, String documentVersion) {
        var documentExists = dmsDocumentRepository.existsById(documentId);

        if (documentExists) {
            log.info("DMS - TransactionId: {} - Documento {} e versão {} encontrado será reprovado", transactionId, documentId, documentVersion);
            var optionalDmsDocumentVersion = dmsDocumentVersionRepository.findByDmsDocumentIdAndVersionNumber(documentId, documentVersion);
            var dmsDocumentversion = optionalDmsDocumentVersion.orElseThrow(() -> new DmsException(String.format("Versão %s não encontrada para document %s", documentId, documentVersion), TypeException.VALID));
            dmsDocumentVersionRepository.delete(dmsDocumentversion);
            amazonS3Service.deleteDocumentS3(amazonS3Service.getBucketName(), dmsDocumentversion.getPathToDocument());
        }

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
        var cpf = dmsUtil.getCpfFromMetadata(jsonMetadata);

        Optional<DmsDocument> optEntity = dmsDocumentRepository.findByCpfAndFilename(cpf, fileName);

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
                                     String documentCategoryName, String documentTypeName, String filename, String comment) {
        try {
            String effectiveFilename = StringUtils.isNotBlank(filename) ? filename : document.getOriginalFilename();
            byte[] documentBytes = document.getBytes();

            ByteArrayResource documentResource = new ByteArrayResource(documentBytes) {
                @Override
                public String getFilename() {
                    return effectiveFilename;
                }
            };

            ByteArrayInputStream documentData = new ByteArrayInputStream(documentBytes);

            return createOrUpdate(transactionId, isFinal, documentData, documentResource, issuingDate, author, metadata, documentCategoryName, documentTypeName, effectiveFilename, comment);
        } catch (IOException e) {
            log.error("DMS - TransactionId: {} - Error processing multipart document", transactionId, e);
            throw new DmsException(environment.getProperty("dms.msg.unknowError"), TypeException.CONFIG, transactionId);
        }
    }

    @Transactional
    public DocumentId createOrUpdate(String transactionId, boolean isFinal, String documentAsBase64, LocalDate issuingDate, String author, String metadata, String documentCategoryName,
                                     String documentTypeName, String filenameDms, String comment) throws IOException {

        final ByteArrayInputStream documentData = new ByteArrayInputStream(Base64.decodeBase64(documentAsBase64));
        final ByteArrayResource documentResource = new ByteArrayResource(Base64.decodeBase64(documentAsBase64)) {
            @Override
            public String getFilename() {
                return filenameDms;
            }
        };

        return createOrUpdate(transactionId, isFinal, documentData, documentResource, issuingDate, author, metadata, documentCategoryName, documentTypeName, filenameDms, comment);
    }

    @Transactional
    private DocumentId createOrUpdate(String transactionId, boolean isFinal, ByteArrayInputStream documentData, ByteArrayResource documentResource, LocalDate issuingDate, String author, String metadata, String documentCategoryName,
                                     String documentTypeName, String filenameDms, String comment) throws IOException {

        Map<String, Object> jsonMetadata = metadataService.getValideMetadata(transactionId, metadata, documentCategoryName, issuingDate);

        var cpf = dmsUtil.getCpfFromMetadata(jsonMetadata);
        Optional<DmsDocument> optEntity = this.dmsDocumentRepository.findByCpfAndFilename(cpf, filenameDms);

        final MimeType mimeType = this.dmsUtil.validateMimeType(transactionId, documentData);
        ByteArrayResource byteArrayResourceSignature = signingService.applyDigitalSignature(mimeType) ? signingService.signPdf(filenameDms, documentResource) : documentResource;
        ByteArrayInputStream inputStreamSignature = new ByteArrayInputStream(byteArrayResourceSignature.getByteArray());
        var entity = optEntity.orElseGet(() -> DmsDocument.of()
                .id(UUID.randomUUID().toString())
                .cpf(cpf)
                .category(documentCategoryName)
                .mimeType(mimeType.getName())
                .filename(filenameDms)
                .build());

        var maxDmsDocumentVersion = dmsDocumentVersionRepository.findLastVersionByDmsDocumentId(entity.getId());

        BigDecimal lastVersion = maxDmsDocumentVersion.map(DmsDocumentVersion::getVersionNumber).orElse(BigDecimal.ZERO);
        var version = dmsUtil.generateVersion(isFinal, lastVersion);
        var documentId = entity.getId();

        long contentLength = byteArrayResourceSignature.contentLength();
        String pathToNewDocument = amazonS3Service.createDocumentS3(filenameDms, cpf, version, inputStreamSignature, contentLength);

        var newVersion = DmsDocumentVersion.of()
                .dmsDocumentId(entity.getId())
                .versionNumber(version)
                .versionType(isFinal ? VersionType.MAJOR : VersionType.MINOR)
                .creationDate(LocalDateTime.now())
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
            dmsDocumentRepository.save(entity);
        } catch(DuplicateKeyException e) {
            log.error("Chave duplicada para criacao de documento filename={}, cpf={}, version={}. Limpando registros orfãos", filenameDms, cpf, version);
            dmsDocumentVersionRepository.deleteById(idNewVersion);
            throw new DuplicateKeyException(String.format("Chave duplicada para criacao de documento filename=%s, cpf=%s, version=%s. Registro ja se encontra na base. Verificar concorrencia nas chamadas", filenameDms, cpf, version));
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
        dmsDocumentRepository.save(entity);

        DocumentId documentIdResponse = new DocumentId(documentId, newVersion.toPlainString());
        return new ResponseEntity<>(documentIdResponse, HttpStatus.OK);
    }

    public UrlPresignedResponse generatePresignedUrl(String transactionId, PayloadUrlPresigned payloadUrlPresigned) throws IOException {
        Map<String, Object> metadata = metadataService.getValideMetadata(transactionId, payloadUrlPresigned.getMetadata(), payloadUrlPresigned.getCategory(), payloadUrlPresigned.getIssuingDate());

        var cpf = dmsUtil.getCpfFromMetadata(metadata);
        Optional<DmsDocument> optEntity = this.dmsDocumentRepository.findByCpfAndFilename(cpf, payloadUrlPresigned.getFileName());

        var entity = optEntity.orElseGet(() -> DmsDocument.of()
                .id(UUID.randomUUID().toString())
                .cpf(cpf)
                .category(payloadUrlPresigned.getCategory())
                .mimeType(payloadUrlPresigned.getMimeType())// todo validar o que colocar pois o base64 não é passado
                .filename(payloadUrlPresigned.getFileName())
                .build());

        var maxDmsDocumentVersion = dmsDocumentVersionRepository.findLastVersionByDmsDocumentId(entity.getId());

        BigDecimal lastVersion = maxDmsDocumentVersion.map(DmsDocumentVersion::getVersionNumber).orElse(BigDecimal.ZERO);
        var version = dmsUtil.generateVersion(payloadUrlPresigned.isFinal(), lastVersion);
        var documentId = entity.getId();

        String pathToNewDocument = amazonS3Service.getPathToDocument(payloadUrlPresigned.getFileName(), cpf, String.valueOf(version));

        var newVersion = DmsDocumentVersion.of()
                .dmsDocumentId(documentId)
                .versionNumber(version)
                .versionType(payloadUrlPresigned.isFinal() ? VersionType.MAJOR : VersionType.MINOR)
                .creationDate(LocalDateTime.now())
                .pathToDocument(pathToNewDocument)
                .fileSize(payloadUrlPresigned.getFileSize()) //todo validar o que colocar pois o base64 não é passado
                .metadata(metadata)
                .author(payloadUrlPresigned.getAuthor())
                .comment(payloadUrlPresigned.getComment())
                .mimeType(payloadUrlPresigned.getMimeType())
                .build();

        String idNewVersion = StringUtils.EMPTY;

        try {
            idNewVersion = dmsDocumentVersionRepository.save(newVersion).getId();
            entity.setMetadata(metadata);
            dmsDocumentRepository.save(entity);
        } catch(DuplicateKeyException e) {
            log.error("Chave duplicada para criacao de documento filename={}, cpf={}, version={}. Limpando registros orfãos", payloadUrlPresigned.getFileName(), cpf, version);
            dmsDocumentVersionRepository.deleteById(idNewVersion);
            throw new DuplicateKeyException(String.format("Chave duplicada para criacao de documento filename=%s, cpf=%s, version=%s. Registro ja se encontra na base. Verificar concorrencia nas chamadas", payloadUrlPresigned.getFileName(), cpf, version));
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

    private DmsDocumentVersion digitalSignatureAndSaveBucket(String transactionId, PayloadApprove payloadApprove, DmsDocumentVersion currentDmsDocumentVersion, DmsDocument entity, BigDecimal newVersion, String mimeType) throws IOException {
        String pathToDocument;
        Long fileSize;

        if (shouldAddSignatureText(payloadApprove)) {
            byte[] documentContent;
            if (StringUtils.isEmpty(currentDmsDocumentVersion.getPathToDocument())) {
                documentContent = amazonS3Service.getDocumentContentFromS3(entity.getFilename(), entity.getCpf(), String.valueOf(currentDmsDocumentVersion.getVersionNumber()));
            } else {
                documentContent = amazonS3Service.getDocumentContentFromS3(currentDmsDocumentVersion.getPathToDocument());
            }

            ByteArrayResource documentWithDigitalSignature = signDocument(documentContent, transactionId, payloadApprove.getSignatureText(), entity.getFilename());
            ByteArrayInputStream documentData = new ByteArrayInputStream(documentWithDigitalSignature.getInputStream().readAllBytes());
            fileSize = documentWithDigitalSignature.contentLength();
            pathToDocument = amazonS3Service.createDocumentS3(entity.getFilename(), entity.getCpf(), newVersion, documentData, fileSize);
        } else {
            pathToDocument = amazonS3Service.copyDocumentS3(entity.getFilename(), entity.getCpf(), newVersion, currentDmsDocumentVersion.getVersionNumber());
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
