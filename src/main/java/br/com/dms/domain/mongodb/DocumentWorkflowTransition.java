package br.com.dms.domain.mongodb;

import br.com.dms.domain.core.DocumentWorkflowStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document
@CompoundIndex(def = "{'tenantId': 1, 'documentId': 1, 'changedAt': -1}", name = "tenant_document_transition_idx")
public class DocumentWorkflowTransition {

    @Id
    private String id;

    private String tenantId;

    private String documentId;

    private DocumentWorkflowStatus fromStatus;

    private DocumentWorkflowStatus toStatus;

    private String actor;

    private String reason;

    private LocalDateTime changedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

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

    public static Builder of() {
        return new Builder();
    }

    public static final class Builder {
        private final DocumentWorkflowTransition transition;

        private Builder() {
            this.transition = new DocumentWorkflowTransition();
        }

        public Builder tenantId(String tenantId) {
            transition.setTenantId(tenantId);
            return this;
        }

        public Builder documentId(String documentId) {
            transition.setDocumentId(documentId);
            return this;
        }

        public Builder fromStatus(DocumentWorkflowStatus fromStatus) {
            transition.setFromStatus(fromStatus);
            return this;
        }

        public Builder toStatus(DocumentWorkflowStatus toStatus) {
            transition.setToStatus(toStatus);
            return this;
        }

        public Builder actor(String actor) {
            transition.setActor(actor);
            return this;
        }

        public Builder reason(String reason) {
            transition.setReason(reason);
            return this;
        }

        public Builder changedAt(LocalDateTime changedAt) {
            transition.setChangedAt(changedAt);
            return this;
        }

        public DocumentWorkflowTransition build() {
            return transition;
        }
    }
}
