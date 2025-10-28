package br.com.dms.service;

import br.com.dms.controller.request.DocumentInformationRequest;
import br.com.dms.domain.mongodb.DmsDocument;
import br.com.dms.domain.mongodb.DmsDocumentVersion;
import br.com.dms.repository.mongo.DmsDocumentRepository;
import br.com.dms.repository.mongo.DmsDocumentVersionRepository;
import br.com.dms.repository.redis.DocumentInformationRepository;
import br.com.dms.service.dto.DocumentContent;
import br.com.dms.domain.core.VersionType;
import br.com.dms.service.workflow.pojo.DmsDocumentSearchResponse;
import br.com.dms.service.workflow.pojo.DmsEntry;
import br.com.dms.service.workflow.pojo.DmsVersions;
import br.com.dms.service.workflow.pojo.ZipFileResponse;
import br.com.dms.service.workflow.pojo.mapper.DmsEntryMapper;
import br.com.dms.service.workflow.pojo.mapper.DmsVersionsMapper;
import br.com.dms.exception.DmsBusinessException;
import br.com.dms.exception.TypeException;
import br.com.dms.util.DmsEntryComparator;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class DocumentQueryService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentQueryService.class);

    private final DocumentInformationRepository documentInformationRepository;

    private final DmsDocumentRepository dmsDocumentRepository;

    private final DmsDocumentVersionRepository dmsDocumentVersionRepository;

    private final ObjectMapper objectMapper;

    private final AmazonS3Service amazonS3Service;

    public DocumentQueryService(DocumentInformationRepository documentInformationRepository,
                                DmsDocumentRepository dmsDocumentRepository,
                                DmsDocumentVersionRepository dmsDocumentVersionRepository,
                                ObjectMapper objectMapper,
                                AmazonS3Service amazonS3Service) {
        this.documentInformationRepository = documentInformationRepository;
        this.dmsDocumentRepository = dmsDocumentRepository;
        this.dmsDocumentVersionRepository = dmsDocumentVersionRepository;
        this.objectMapper = objectMapper;
        this.amazonS3Service = amazonS3Service;
    }

    public DocumentContent getDocumentContent(String transactionId, String documentId, Optional<String> version) {
        Optional<DmsDocument> optEntity = dmsDocumentRepository.findById(documentId);

        if (optEntity.isEmpty()) {
            logger.debug("TransactionId: {} - Document {} not found", transactionId, documentId);
            return DocumentContent.empty();
        }

        DmsDocument entity = optEntity.get();
        DmsDocumentVersion versionEntity = resolveDocumentVersion(transactionId, documentId, version);

        if (versionEntity == null) {
            logger.debug("TransactionId: {} - No version found for document {} using key {}", transactionId, documentId, version.orElse("latest"));
            return DocumentContent.empty();
        }

        try {
            byte[] content = loadDocumentContent(entity, versionEntity);
            String mimeType = Objects.nonNull(versionEntity.getMimeType()) ? versionEntity.getMimeType() : entity.getMimeType();
            return new DocumentContent(content, mimeType);
        } catch (AmazonS3Exception s3Exception) {
            if (s3Exception.getStatusCode() == 404) {
                logger.error("Document missing in bucket. Id={}, Version={}, Size={}", versionEntity.getDmsDocumentId(), versionEntity.getVersionNumber(), versionEntity.getFileSize());
            } else {
                logger.error("Error fetching document from bucket", s3Exception);
                throw new DmsBusinessException(s3Exception.getMessage(), TypeException.VALID, transactionId);
            }
        } catch (Exception exception) {
            throw new DmsBusinessException(exception.getMessage(), TypeException.VALID, transactionId);
        }

        return DocumentContent.empty();
    }

    public ResponseEntity<String> getDocumentInformation(String documentId, Optional<String> version) {
        Optional<String> optionalInformation = documentInformationRepository.get(documentId, version.orElse(null));
        if (optionalInformation.isPresent()) {
            logger.info("Encontrou metadados do documento {} e versao {} no cache", documentId, version);
            return ResponseEntity.ok(optionalInformation.get());
        }

        ResponseEntity<String> information = ResponseEntity.notFound().build();
        Optional<DmsDocument> optEntity = dmsDocumentRepository.findById(documentId);

        if (optEntity.isPresent()) {
            DmsDocumentSearchResponse response = new DmsDocumentSearchResponse();
            DmsDocument entity = optEntity.get();
            if (version.isPresent()) {
                var optVersion = dmsDocumentVersionRepository.findByDmsDocumentIdAndVersionNumber(documentId, version.get());
                if (optVersion.isPresent()) {
                    var dmsVersion = optVersion.get();
                    Map<String, Object> metadados = dmsVersion.getMetadata();
                    var mimeType = Objects.nonNull(dmsVersion.getMimeType()) ? dmsVersion.getMimeType() : entity.getMimeType();
                    var dmsEntry = DmsEntryMapper.of(entity.getId(), dmsVersion.getCreationDate(), dmsVersion.getModifiedAt(), entity.getFilename(), entity.getCategory(), mimeType, dmsVersion.getFileSize(), metadados, dmsVersion.getVersionNumber().toPlainString(), dmsVersion.getVersionType().name());
                    response.setEntry(dmsEntry);
                }
            } else {
                var lastVersion = dmsDocumentVersionRepository.findLastVersionByDmsDocumentId(documentId).orElse(null);
                if (lastVersion != null) {
                    Map<String, Object> metadados = lastVersion.getMetadata();
                    var mimeType = Objects.nonNull(lastVersion.getMimeType()) ? lastVersion.getMimeType() : entity.getMimeType();
                    var dmsEntry = DmsEntryMapper.of(entity.getId(), lastVersion.getCreationDate(), lastVersion.getModifiedAt(), entity.getFilename(), entity.getCategory(), mimeType, lastVersion.getFileSize(), metadados, lastVersion.getVersionNumber().toPlainString(), lastVersion.getVersionType().name());
                    response.setEntry(dmsEntry);
                }
            }

            information = new ResponseEntity(response, HttpStatus.OK);

            try {
                documentInformationRepository.save(documentId, version.orElse(null), Objects.requireNonNull(objectMapper.writeValueAsString(information.getBody())));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }

            logger.info("Buscou metadados no DMS e incluiu no cache para subsequente consultas do documento {} e versao {}", documentId, version);
        }

        return information;
    }

    public ResponseEntity<DmsVersions> getDocumentVersions(String documentId) {
        var optionalEntity = this.dmsDocumentRepository.findById(documentId);
        var listVersions = new ArrayList<DmsEntry>();

        if (optionalEntity.isPresent()) {
            var entity = optionalEntity.get();
            var mongoVersions = this.dmsDocumentVersionRepository.findByDmsDocumentId(documentId).orElse(new ArrayList<>());

            for (DmsDocumentVersion dmsDocumentVersion : mongoVersions) {
                var mimeType = Objects.nonNull(dmsDocumentVersion.getMimeType()) ? dmsDocumentVersion.getMimeType() : entity.getMimeType();
                Map<String, Object> metadados = dmsDocumentVersion.getMetadata();
                DmsEntry dmsEntry = DmsEntryMapper.of(String.valueOf(dmsDocumentVersion.getVersionNumber()), dmsDocumentVersion.getCreationDate(), dmsDocumentVersion.getModifiedAt(),
                        entity.getFilename(), entity.getCategory(), mimeType, dmsDocumentVersion.getFileSize(), metadados, dmsDocumentVersion.getVersionNumber().toPlainString(), dmsDocumentVersion.getVersionType().name());

                listVersions.add(dmsEntry);
            }
        }

        //ordena de forma natural as versoes
        Collections.sort(listVersions, Collections.reverseOrder(new DmsEntryComparator()));
        return ResponseEntity.ok(DmsVersionsMapper.of(listVersions));
    }

    public byte[] zipDocuments(List<DocumentInformationRequest> documentsInformation, String transactionId) throws IOException  {
        logger.info("TransactionId: {}, Quantidade de documentos para compactar {}", transactionId, documentsInformation.size());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        var arquivosCompactados = 0;
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (DocumentInformationRequest documentInformation : documentsInformation) {
                ZipFileResponse zipFileResponse = getEntry(documentInformation, transactionId);
                if(zipFileResponse != null){
                    logger.info("TransactionId: {} - compactando documneto: {}, versao: {}",transactionId, documentInformation.getDocumentId(), documentInformation.getVersion());
                    zos.putNextEntry(zipFileResponse.getZipEntry());
                    zos.write(java.util.Base64.getDecoder().decode(zipFileResponse.getBase64String()));
                    zos.closeEntry();
                    arquivosCompactados++;
                }
            }
        }
        logger.info("TransactionId: {} - arquivos compactados no formato zip", transactionId);
        logger.info("TransactionId: {} - quantidade de arquivos compactados: {}",transactionId,  arquivosCompactados);
        return baos.toByteArray();
    }

    private DmsDocumentVersion resolveDocumentVersion(String transactionId, String documentId, Optional<String> version) {
        if (version.isPresent()) {
            logger.info("TransactionId: {} - Fetching version {} for document {}", transactionId, version.get(), documentId);
            return dmsDocumentVersionRepository.findByDmsDocumentIdAndVersionNumber(documentId, version.get()).orElse(null);
        }

        logger.info("TransactionId: {} - Fetching latest version for document {}", transactionId, documentId);
        return dmsDocumentVersionRepository.findLastVersionByDmsDocumentId(documentId).orElse(null);
    }

    private byte[] loadDocumentContent(DmsDocument document, DmsDocumentVersion version) throws IOException {
        if (StringUtils.isEmpty(version.getPathToDocument())) {
            return amazonS3Service.getDocumentContentFromS3(document.getFilename(), document.getCpf(), String.valueOf(version.getVersionNumber()));
        }
        return amazonS3Service.getDocumentContentFromS3(version.getPathToDocument());
    }


    private String getFileExtensionFromMimeType(String mimeType) {
        String[] mimeTypeArr = mimeType.split("/");
        return mimeTypeArr[1];
    }

    public ZipFileResponse getEntry(DocumentInformationRequest documentInformation, String transactionId) {
        byte[] content = new byte[0];
        DmsDocumentVersion dmsDocumentVersionEntity = null;
        DmsDocument entity = null;
        Optional<DmsDocument> optEntity = this.dmsDocumentRepository.findById(documentInformation.getDocumentId());
        ZipFileResponse zipFileResponse = new ZipFileResponse();
        if (optEntity.isPresent()) {
            entity = optEntity.get();
            logger.info("TransactionId: {} - Documento: {} no novo DMS", transactionId, documentInformation.getDocumentId());

            var version = Optional.ofNullable(documentInformation.getVersion());
            if (version.isPresent()) {
                logger.info("TransactionId: {} - Encontrada a versao {} do documento {} no novo DMS", transactionId,documentInformation.getVersion(), documentInformation.getDocumentId());
                var versionOpt = this.dmsDocumentVersionRepository.findByDmsDocumentIdAndVersionNumber(entity.getId(), version.get());
                if (versionOpt.isPresent()) {
                    dmsDocumentVersionEntity = versionOpt.get();
                }
            } else {
                logger.info("TransactionId: {} - Buscando a ultima versao do documento {} no novo DMS", transactionId,documentInformation.getDocumentId());
                dmsDocumentVersionEntity = this.dmsDocumentVersionRepository.findLastVersionByDmsDocumentId(entity.getId()).orElse(null);
            }
            try {
                if (dmsDocumentVersionEntity != null) {
                    content = loadDocumentContent(entity, dmsDocumentVersionEntity);
                }
            } catch (AmazonS3Exception s3Exception) {
                if (s3Exception.getStatusCode() == 404) {
                    logger.error("Não foi encontrado documento no bucket. Arquivo pode estar corrompido. Id={}, Version={}, Size={}", dmsDocumentVersionEntity.getDmsDocumentId(), dmsDocumentVersionEntity.getVersionNumber(), dmsDocumentVersionEntity.getFileSize());
                } else {
                    logger.error("Erro ao consultar documento no bucket. Erro={}", s3Exception.getCause());
                    throw new DmsBusinessException(s3Exception.getMessage(), TypeException.VALID);
                }
            } catch (Exception e) {
                throw new DmsBusinessException(e.getMessage(), TypeException.VALID);
            }
        }

        if (content.length == 0){
            return null;
        }

        logger.info("TransactionId: {} - compactando documneto: {}, versao: {}",transactionId, documentInformation.getDocumentId(), documentInformation.getVersion());
        String base64String = Base64.encodeBase64String(content);
        ZipEntry entry = new ZipEntry(getEntryName(entity, dmsDocumentVersionEntity));
        zipFileResponse.setZipEntry(entry);
        zipFileResponse.setBase64String(base64String);
        return zipFileResponse;
    }

    private String getEntryName(DmsDocument entity, DmsDocumentVersion dmsDocumentVersionEntity){
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd_MM_yyyy_HH_mm");
        var creationDateFormatted = dmsDocumentVersionEntity.getCreationDate().format(formatter);
        var versionFormatted = "v" + dmsDocumentVersionEntity.getVersionNumber().toString().replace(".","");
        var filenameDms = entity.getFilename().split(Pattern.quote("."))[0];
        return filenameDms + "_" + creationDateFormatted + "_" + versionFormatted + "." + getFileExtensionFromMimeType(entity.getMimeType());

    }
}
