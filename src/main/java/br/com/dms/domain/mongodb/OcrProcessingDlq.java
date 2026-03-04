package br.com.dms.domain.mongodb;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "ocrProcessingDlq")
@CompoundIndex(def = "{'tenantId': 1, 'documentId': 1, 'version': 1}", name = "tenant_document_version_dlq_idx")
public class OcrProcessingDlq {

    @Id
    private String id;
    private String tenantId;
    private String documentId;
    private String version;
    private String pathToDocument;
    private String mimeType;
    private Integer attempts;
    private String error;
    private LocalDateTime createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getPathToDocument() { return pathToDocument; }
    public void setPathToDocument(String pathToDocument) { this.pathToDocument = pathToDocument; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public Integer getAttempts() { return attempts; }
    public void setAttempts(Integer attempts) { this.attempts = attempts; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
