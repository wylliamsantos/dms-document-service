package br.com.dms.domain.mongodb;

import br.com.dms.domain.core.DocumentWorkflowStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

@Document
@CompoundIndex(def = "{'category': 1, 'cpf': 1}", name = "cpf_category_index")
public class DmsDocument {

    @Id
    private String id;

    private String filename;

    private String category;

    @Indexed(name = "cpf_index")
    private String cpf;

    private String mimeType;

    private Map<String, Object> metadata;

    private DocumentWorkflowStatus workflowStatus;

    public DmsDocument() {
    }

    public DmsDocument(String id, String filename, String category, String mimeType, String cpf, Map<String, Object> metadata) {
        this.id = id;
        this.filename = filename;
        this.category = category;
        this.mimeType = mimeType;
        this.cpf = cpf;
        this.metadata = metadata;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public DocumentWorkflowStatus getWorkflowStatus() {
        return workflowStatus;
    }

    public void setWorkflowStatus(DocumentWorkflowStatus workflowStatus) {
        this.workflowStatus = workflowStatus;
    }

    public static DmsDocument.Builder of() {
        return new DmsDocument.Builder();
    }

    public static final class Builder {

        private DmsDocument dmsDocument;

        private Builder() {
            dmsDocument = new DmsDocument();
        }

        public Builder id(String id) {
            dmsDocument.setId(id);
            return this;
        }

        public Builder filename(String filename) {
            dmsDocument.setFilename(filename);
            return this;
        }

        public Builder category(String category) {
            dmsDocument.setCategory(category);
            return this;
        }

        public Builder mimeType(String mimeType) {
            dmsDocument.setMimeType(mimeType);
            return this;
        }

        public Builder cpf(String cpf) {
            dmsDocument.setCpf(cpf);
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            dmsDocument.setMetadata(metadata);
            return this;
        }

        public Builder workflowStatus(DocumentWorkflowStatus workflowStatus) {
            dmsDocument.setWorkflowStatus(workflowStatus);
            return this;
        }

        public DmsDocument build() {
            return dmsDocument;
        }

    }


}
