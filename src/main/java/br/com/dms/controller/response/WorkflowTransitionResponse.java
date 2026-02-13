package br.com.dms.controller.response;

import br.com.dms.domain.core.DocumentWorkflowStatus;

import java.time.LocalDateTime;

public class WorkflowTransitionResponse {

    private DocumentWorkflowStatus fromStatus;
    private DocumentWorkflowStatus toStatus;
    private String actor;
    private String reason;
    private LocalDateTime changedAt;

    public DocumentWorkflowStatus getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(DocumentWorkflowStatus fromStatus) {
        this.fromStatus = fromStatus;
    }

    public DocumentWorkflowStatus getToStatus() {
        return toStatus;
    }

    public void setToStatus(DocumentWorkflowStatus toStatus) {
        this.toStatus = toStatus;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(LocalDateTime changedAt) {
        this.changedAt = changedAt;
    }
}
