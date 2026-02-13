package br.com.dms.controller.response;

import br.com.dms.domain.core.DocumentWorkflowStatus;

public class WorkflowReviewResponse {

    private String documentId;
    private DocumentWorkflowStatus workflowStatus;
    private String message;

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public DocumentWorkflowStatus getWorkflowStatus() {
        return workflowStatus;
    }

    public void setWorkflowStatus(DocumentWorkflowStatus workflowStatus) {
        this.workflowStatus = workflowStatus;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
