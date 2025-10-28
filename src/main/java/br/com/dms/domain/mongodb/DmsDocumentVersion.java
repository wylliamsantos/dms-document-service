package br.com.dms.domain.mongodb;


import br.com.dms.domain.core.VersionType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Document
@CompoundIndex(def = "{'dmsDocumentId': 1, 'versionNumber': 1}", unique = true)
public class DmsDocumentVersion implements Serializable {

    @Id
    private String id;

    private String dmsDocumentId;

    private BigDecimal versionNumber;

    private VersionType versionType;

    private Long fileSize;

    private LocalDateTime creationDate;

    private LocalDateTime modifiedAt;

    private String pathToDocument;

    private String author;

    private String comment;

    private Boolean alfrescoMigrated;

    private Map<String, Object> metadata;

    private String mimeType;

    public DmsDocumentVersion() {
        super();
    }

    public DmsDocumentVersion(String id, String dmsDocumentId, BigDecimal versionNumber, VersionType versionType, Long fileSize, LocalDateTime creationDate, LocalDateTime modifiedAt,
                              String pathToDocument, String author, String comment, Boolean alfrescoMigrated, Map<String, Object> metadata) {
        this.id = id;
        this.dmsDocumentId = dmsDocumentId;
        this.versionNumber = versionNumber;
        this.versionType = versionType;
        this.fileSize = fileSize;
        this.creationDate = creationDate;
        this.modifiedAt = modifiedAt;
        this.pathToDocument = pathToDocument;
        this.author = author;
        this.comment = comment;
        this.alfrescoMigrated = alfrescoMigrated;
        this.metadata = metadata;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDmsDocumentId() {
        return dmsDocumentId;
    }

    public void setDmsDocumentId(String dmsDocumentId) {
        this.dmsDocumentId = dmsDocumentId;
    }

    public BigDecimal getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(BigDecimal versionNumber) {
        this.versionNumber = versionNumber;
    }

    public VersionType getVersionType() {
        return versionType;
    }

    public void setVersionType(VersionType versionType) {
        this.versionType = versionType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public LocalDateTime getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(LocalDateTime creationDate) {
        this.creationDate = creationDate;
    }

    public LocalDateTime getModifiedAt() {
        return modifiedAt;
    }

    public void setModifiedAt(LocalDateTime modifiedAt) {
        this.modifiedAt = modifiedAt;
    }

    public String getPathToDocument() {
        return pathToDocument;
    }

    public void setPathToDocument(String pathToDocument) {
        this.pathToDocument = pathToDocument;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Boolean getAlfrescoMigrated() {
        return alfrescoMigrated;
    }

    public void setAlfrescoMigrated(Boolean alfrescoMigrated) {
        this.alfrescoMigrated = alfrescoMigrated;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public static Builder of() {
        return new Builder();
    }

    public static final class Builder {
        private DmsDocumentVersion dmsDocumentVersion;

        private Builder() {
            dmsDocumentVersion = new DmsDocumentVersion();
        }

        public Builder id(String id) {
            dmsDocumentVersion.setId(id);
            return this;
        }

        public Builder dmsDocumentId(String dmsDocumentId) {
            dmsDocumentVersion.setDmsDocumentId(dmsDocumentId);
            return this;
        }

        public Builder versionNumber(BigDecimal versionNumber) {
            dmsDocumentVersion.setVersionNumber(versionNumber);
            return this;
        }

        public Builder versionType(VersionType versionType) {
            dmsDocumentVersion.setVersionType(versionType);
            return this;
        }

        public Builder fileSize(Long fileSize) {
            dmsDocumentVersion.setFileSize(fileSize);
            return this;
        }

        public Builder creationDate(LocalDateTime creationDate) {
            dmsDocumentVersion.setCreationDate(creationDate);
            return this;
        }

        public Builder modifiedAt(LocalDateTime modifiedAt) {
            dmsDocumentVersion.setModifiedAt(modifiedAt);
            return this;
        }

        public Builder pathToDocument(String pathToDocument) {
            dmsDocumentVersion.setPathToDocument(pathToDocument);
            return this;
        }

        public Builder author(String author) {
            dmsDocumentVersion.setAuthor(author);
            return this;
        }

        public Builder comment(String comment) {
            dmsDocumentVersion.setComment(comment);
            return this;
        }

        public Builder alfrescoMigrated(Boolean alfrescoMigrated) {
            dmsDocumentVersion.setAlfrescoMigrated(alfrescoMigrated);
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            dmsDocumentVersion.setMetadata(metadata);
            return this;
        }

        public Builder mimeType(String mimeType) {
            dmsDocumentVersion.setMimeType(mimeType);
            return this;
        }

        public DmsDocumentVersion build() {
            return dmsDocumentVersion;
        }
    }
}
