package br.com.dms.controller.response;

import br.com.dms.domain.core.DocumentWorkflowStatus;

import java.time.LocalDateTime;

public class PendingDocumentResponse {

    private String documentId;
    private String filename;
    private String category;
    private DocumentWorkflowStatus workflowStatus;
    private String currentVersion;
    private String author;
    private String businessKeyType;
    private String businessKeyValue;
    private LocalDateTime updatedAt;

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public DocumentWorkflowStatus getWorkflowStatus() {
        return workflowStatus;
    }

    public void setWorkflowStatus(DocumentWorkflowStatus workflowStatus) {
        this.workflowStatus = workflowStatus;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(String currentVersion) {
        this.currentVersion = currentVersion;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getBusinessKeyType() {
        return businessKeyType;
    }

    public void setBusinessKeyType(String businessKeyType) {
        this.businessKeyType = businessKeyType;
    }

    public String getBusinessKeyValue() {
        return businessKeyValue;
    }

    public void setBusinessKeyValue(String businessKeyValue) {
        this.businessKeyValue = businessKeyValue;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
