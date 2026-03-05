package br.com.dms.controller.response;

import br.com.dms.domain.core.DocumentWorkflowStatus;

public class WorkflowCategoryStatusCountResponse {

    private String category;

    private DocumentWorkflowStatus status;

    private long count;

    public WorkflowCategoryStatusCountResponse() {
    }

    public WorkflowCategoryStatusCountResponse(String category, DocumentWorkflowStatus status, long count) {
        this.category = category;
        this.status = status;
        this.count = count;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public DocumentWorkflowStatus getStatus() {
        return status;
    }

    public void setStatus(DocumentWorkflowStatus status) {
        this.status = status;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }
}
