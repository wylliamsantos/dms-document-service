package br.com.dms.controller.response;

import br.com.dms.domain.core.DocumentWorkflowStatus;

public class WorkflowStatusCountResponse {

    private DocumentWorkflowStatus status;

    private long count;

    public WorkflowStatusCountResponse() {
    }

    public WorkflowStatusCountResponse(DocumentWorkflowStatus status, long count) {
        this.status = status;
        this.count = count;
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
