package br.com.dms.controller;

import br.com.dms.controller.request.*;
import br.com.dms.controller.response.UrlPresignedResponse;
import br.com.dms.domain.core.DocumentId;
import br.com.dms.exception.DefaultError;
import br.com.dms.service.DocumentDeleteService;
import br.com.dms.service.DocumentQueryService;
import br.com.dms.service.dto.DocumentContent;
import br.com.dms.service.DmsService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import static br.com.dms.config.AuthorizationRules.MANAGE;
import static br.com.dms.config.AuthorizationRules.READ;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/v1/documents")
@Slf4j
@PreAuthorize(READ)
public class DocumentController {

    private static final String API_VERSION = "v1";

    private final DocumentDeleteService documentDeleteService;
    private final DocumentQueryService documentQueryService;
    private final DmsService dmsService;

    public DocumentController(DocumentDeleteService documentDeleteService,
                              DocumentQueryService documentQueryService,
                              DmsService dmsService) {
        this.documentDeleteService = documentDeleteService;
        this.documentQueryService = documentQueryService;
        this.dmsService = dmsService;
    }

    @DeleteMapping(value = "/{documentId}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "No Content"),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "documentId not found"),
            @ApiResponse(responseCode = "417", description = "Business Error", content = {@Content(schema = @Schema(implementation = DefaultError.class))}),
            @ApiResponse(responseCode = "500", description = "Server Error", content = {@Content(schema = @Schema(implementation = DefaultError.class))})
    })
    @PreAuthorize(MANAGE)
    public ResponseEntity<?> delete(@RequestHeader(name = "TransactionId") String transactionId,
                                    @RequestHeader(name = "Authorization") String authorization,
                                    @PathVariable(value = "documentId") String documentId) {

        log.info("DMS version {} - TransactionId: {} - Delete document - documentId: {}", API_VERSION, transactionId, documentId);
        return documentDeleteService.delete(transactionId, documentId);
    }

    @GetMapping(path = {"/{documentId}/base64", "/{documentId}/{version}/base64"}, produces = MediaType.TEXT_PLAIN_VALUE)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok", content = {@Content(schema = @Schema(implementation = Byte[].class))}),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "documentId not found"),
            @ApiResponse(responseCode = "500", description = "Server Error", content = {@Content(schema = @Schema(implementation = DefaultError.class))})
    })
    public ResponseEntity<?> getBase64(@RequestHeader(name = "TransactionId") String transactionId,
                                       @RequestHeader(name = "Authorization") String authorization,
                                       @PathVariable(value = "documentId") String documentId,
                                       @PathVariable(name = "version", required = false) Optional<String> version) {

        log.info("DMS version {} - TransactionId: {} - Get document base64 - documentId: {}", API_VERSION, transactionId, documentId);
        DocumentContent documentContent = documentQueryService.getDocumentContent(transactionId, documentId, version);
        return ResponseEntity.ok(Base64.encodeBase64String(documentContent.content()));
    }

    @Hidden
    @PostMapping(path = "/zip", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok", content = {@Content(schema = @Schema(implementation = Byte[].class))}),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "documentId not found"),
            @ApiResponse(responseCode = "500", description = "Server Error", content = {@Content(schema = @Schema(implementation = DefaultError.class))})
    })
    public ResponseEntity<?> zip(@RequestHeader(name = "TransactionId") String transactionId,
                                 @RequestHeader(name = "Authorization") String authorization,
                                 @RequestBody List<DocumentInformationRequest> documentsInformation) throws IOException {

        log.info("DMS version {} - Download Zip Files - TransactionId: {}", API_VERSION, transactionId);
        var zipContent = documentQueryService.zipDocuments(documentsInformation, transactionId);
        return ResponseEntity
                .ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/zip")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=documents.zip")
                .body(zipContent);
    }

    @GetMapping(path = {"/{documentId}/content", "/{documentId}/{version}/content"}, produces = {MediaType.APPLICATION_OCTET_STREAM_VALUE, MediaType.APPLICATION_JSON_VALUE})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok", content = {@Content(schema = @Schema(implementation = Byte[].class))}),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "documentId not found"),
            @ApiResponse(responseCode = "500", description = "Server Error", content = {@Content(schema = @Schema(implementation = DefaultError.class))})
    })
    public ResponseEntity<?> get(@RequestHeader(name = "TransactionId") String transactionId,
                                 @RequestHeader(name = "Authorization") String authorization,
                                 @PathVariable(value = "documentId") String documentId,
                                 @PathVariable(name = "version", required = false) Optional<String> version) {

        DocumentContent documentContent = documentQueryService.getDocumentContent(transactionId, documentId, version);
        HttpHeaders headers = new HttpHeaders();
        if (documentContent.mimeType() != null) {
            headers.add(HttpHeaders.CONTENT_TYPE, documentContent.mimeType());
        }
        return new ResponseEntity<>(documentContent.content(), headers, HttpStatus.OK);
    }

    @GetMapping(path = "/{documentId}/versions", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok", content = {@Content(schema = @Schema(implementation = Byte[].class))}),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "documentId not found"),
            @ApiResponse(responseCode = "500", description = "Server Error", content = {@Content(schema = @Schema(implementation = DefaultError.class))})
    })
    public ResponseEntity<?> getVersions(@RequestHeader(name = "TransactionId") String transactionId,
                                         @RequestHeader(name = "Authorization") String authorization,
                                         @PathVariable(value = "documentId") String documentId) {

        log.info("DMS version {} - TransactionId: {} - Get document versions - documentId: {}", API_VERSION, transactionId, documentId);
        return documentQueryService.getDocumentVersions(documentId);
    }

    @GetMapping(path = {"/{documentId}/information", "/{documentId}/{version}/information"}, produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok", content = {@Content(schema = @Schema(implementation = Byte[].class))}),
            @ApiResponse(responseCode = "404", description = "documentId not found"),
            @ApiResponse(responseCode = "500", description = "Server Error", content = {@Content(schema = @Schema(implementation = DefaultError.class))})
    })
    public ResponseEntity<?> getDocumentInformation(@RequestHeader(name = "TransactionId") String transactionId,
                                                    @RequestHeader(name = "Authorization") String authorization,
                                                    @PathVariable(value = "documentId") String documentId,
                                                    @PathVariable(name = "version", required = false) Optional<String> version) {
        log.info("DMS version {} - TransactionId: {} - document information - documentId: {}, version: {}", API_VERSION, transactionId, documentId, version);
        return documentQueryService.getDocumentInformation(documentId, version);
    }

    @PostMapping(path = "/multipart", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Created", content = {@Content(schema = @Schema(implementation = DocumentId.class))}),
            @ApiResponse(responseCode = "200", description = "Updated", content = {@Content(schema = @Schema(implementation = DocumentId.class))}),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "417", description = "Business Error", content = {@Content(schema = @Schema(implementation = DefaultError.class))}),
            @ApiResponse(responseCode = "413", description = "Payload too large"),
            @ApiResponse(responseCode = "500", description = "Server Error", content = {@Content(schema = @Schema(implementation = DefaultError.class))})
    })
    @PreAuthorize(MANAGE)
    public ResponseEntity<?> createOrUpdate(@RequestHeader(name = "TransactionId") String transactionId,
                                            @RequestHeader(name = "Authorization") String authorization,
                                            @RequestParam(name = "comment", required = false) String comment,
                                            @RequestParam(name = "category") String category,
                                            @RequestParam(name = "issuingDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issuingDate,
                                            @RequestParam(name = "metadata") String metadata,
                                            @RequestParam(name = "filename", required = false) String filename,
                                            @RequestParam(name = "author", required = false) String author,
                                            @RequestParam(name = "document") MultipartFile document,
                                            @RequestParam(name = "isFinal", required = false, defaultValue = "false") boolean isFinal) {

        log.info("DMS version v1 - TransactionId: {} - Upsert document (multipart) - comment: {} - metadata: {} - filename: {}", transactionId, comment, metadata, filename);

        DocumentId documentId = dmsService.createOrUpdate(transactionId, isFinal, document, issuingDate, author, metadata, category, filename, comment);
        return ResponseEntity.status(HttpStatus.CREATED).body(documentId);
    }

    @PostMapping(path = "/base64", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = DocumentId.class))),
            @ApiResponse(responseCode = "200", description = "Updated", content = @Content(schema = @Schema(implementation = DocumentId.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "417", description = "Business Error", content = {@Content(schema = @Schema(implementation = DefaultError.class))}),
            @ApiResponse(responseCode = "413", description = "Payload too large"),
            @ApiResponse(responseCode = "500", description = "Server Error", content = {@Content(schema = @Schema(implementation = DefaultError.class))})
    })
    @PreAuthorize(MANAGE)
    public ResponseEntity<DocumentId> createOrUpdate(@RequestHeader(name = "transactionId") String transactionId,
                                                     @RequestHeader(name = "Authorization") String authorization,
                                                     @RequestBody @Valid PayloadDocument payloadDocument) throws IOException {
        log.info("DMS version v1 - TransactionId: {} - Upsert document via base64 - payload {}", transactionId, payloadDocument);

        try {
            String metadataJson = payloadDocument.getMetadados() != null ?
                new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payloadDocument.getMetadados()) : "{}";
            var documentId = dmsService.createOrUpdate(transactionId, payloadDocument.isFinal(), payloadDocument.getDocumentBase64(), payloadDocument.getIssuingDate(),
                    payloadDocument.getAuthor(), metadataJson, payloadDocument.getCategory(), payloadDocument.getFilename(), payloadDocument.getComment());

            return ResponseEntity.status(HttpStatus.CREATED).body(documentId);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Erro ao converter metadados para JSON", e);
        }
    }

    @PostMapping(path = {"/{id}/{version}/approve"}, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Approved", content = {@Content(schema = @Schema(implementation = DocumentId.class))}),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "417", description = "Business Error", content = {@Content(schema = @Schema(implementation = DefaultError.class))}),
            @ApiResponse(responseCode = "413", description = "Payload too large"),
            @ApiResponse(responseCode = "500", description = "Server Error", content = {@Content(schema = @Schema(implementation = DefaultError.class))})
    })
    @PreAuthorize(MANAGE)
    public ResponseEntity<?> approve(@RequestHeader(name = "transactionId") String transactionId,
                                     @RequestHeader(name = "Authorization") String authorization,
                                     @PathVariable(value = "id") String documentId,
                                     @PathVariable(name = "version") String documentVersion,
                                     @RequestBody(required = false) @Valid PayloadApprove payloadApprove) throws IOException {
        log.info("DMS version v1 - TransactionId: {} - Approve document {} version {} payload {}", transactionId, documentId, documentVersion, payloadApprove);
        ResponseEntity<DocumentId> result = dmsService.approveWithSignatureText(documentId, documentVersion, transactionId, payloadApprove);
        log.info("DMS version v1 - TransactionId: {} - Approve result {} - New version: {}", transactionId, result.getStatusCode(), result.getBody());
        return result;
    }

    @PostMapping(path = {"/{id}/{version}/reprove"}, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Approved", content = {@Content(schema = @Schema(implementation = DocumentId.class))}),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "417", description = "Business Error", content = {@Content(schema = @Schema(implementation = DefaultError.class))}),
            @ApiResponse(responseCode = "413", description = "Payload too large"),
            @ApiResponse(responseCode = "500", description = "Server Error", content = {@Content(schema = @Schema(implementation = DefaultError.class))})
    })
    @PreAuthorize(MANAGE)
    public ResponseEntity<?> reprove(@RequestHeader(name = "transactionId") String transactionId,
                                     @RequestHeader(name = "Authorization") String authorization,
                                     @PathVariable(value = "id") String documentId,
                                     @PathVariable(name = "version") String documentVersion) {
        log.info("DMS version v1 - TransactionId: {} - Reprove document {} version {}", transactionId, documentId, documentVersion);
        ResponseEntity<?> result = dmsService.reprove(transactionId, documentId, documentVersion);
        log.info("DMS version v1 - TransactionId: {} - Reprove result {}", transactionId, result.getStatusCode());
        return result;
    }

    @PutMapping(value = "/{documentId}/metadata", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "409", description = "Conflict"),
            @ApiResponse(responseCode = "417", description = "Business Error", content = {@Content(schema = @Schema(implementation = DefaultError.class))}),
            @ApiResponse(responseCode = "500", description = "Server Error", content = {@Content(schema = @Schema(implementation = DefaultError.class))})
    })
    @PreAuthorize(MANAGE)
    public ResponseEntity<?> updateMetadata(@RequestHeader(name = "TransactionId") String transactionId,
                                            @RequestHeader(name = "Authorization") String authorization,
                                            @PathVariable(value = "documentId") String documentId,
                                            @RequestBody @Valid PayloadMetadata payloadMetadata) {

        log.info("DMS version v1 - TransactionId: {} - Update metadata - documentId: {} - data: {}", transactionId, documentId, payloadMetadata);

        return dmsService.updateMetadata(transactionId, documentId, payloadMetadata.getProperties(), payloadMetadata.getFileName());
    }

    @PostMapping(value = "/presigned/url", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "409", description = "Conflict"),
            @ApiResponse(responseCode = "417", description = "Business Error", content = {@Content(schema = @Schema(implementation = DefaultError.class))}),
            @ApiResponse(responseCode = "500", description = "Server Error", content = {@Content(schema = @Schema(implementation = DefaultError.class))})
    })
    @PreAuthorize(MANAGE)
    public ResponseEntity<UrlPresignedResponse> generatePresignedUrl(@RequestHeader(name = "TransactionId") String transactionId,
                                                                     @RequestHeader(name = "Authorization") String authorization,
                                                                     @RequestBody @Valid PayloadUrlPresigned payloadUrlPresigned) throws IOException {

        log.info("DMS version {}} - TransactionId: {} - Generate presigned URL", API_VERSION, transactionId);
        var response = dmsService.generatePresignedUrl(transactionId, payloadUrlPresigned);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PutMapping(value = "/{documentId}/finalize", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "417", description = "Business Error", content = {@Content(schema = @Schema(implementation = DefaultError.class))}),
            @ApiResponse(responseCode = "500", description = "Server Error", content = {@Content(schema = @Schema(implementation = DefaultError.class))})
    })
    @PreAuthorize(MANAGE)
    public ResponseEntity<DocumentId> finalizeUpload(@RequestHeader(name = "TransactionId") String transactionId,
                                                     @RequestHeader(name = "Authorization") String authorization,
                                                     @PathVariable("documentId") String documentId,
                                                     @RequestBody @Valid FinalizeUploadRequest request) {
        log.info("DMS version {} - TransactionId: {} - Finalize upload document {} version {}", API_VERSION, transactionId, documentId, request.getVersion());
        var documentIdResponse = dmsService.finalizeUpload(transactionId, documentId, request);
        return ResponseEntity.ok(documentIdResponse);
    }
}
