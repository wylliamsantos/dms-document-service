package br.com.dms.controller.response;

import java.util.List;

public class DocumentVersionDiffResponse {

    private final String documentId;
    private final String baseVersion;
    private final String targetVersion;
    private final List<MetadataChange> metadataChanges;

    public DocumentVersionDiffResponse(String documentId,
                                       String baseVersion,
                                       String targetVersion,
                                       List<MetadataChange> metadataChanges) {
        this.documentId = documentId;
        this.baseVersion = baseVersion;
        this.targetVersion = targetVersion;
        this.metadataChanges = metadataChanges;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getBaseVersion() {
        return baseVersion;
    }

    public String getTargetVersion() {
        return targetVersion;
    }

    public List<MetadataChange> getMetadataChanges() {
        return metadataChanges;
    }

    public static class MetadataChange {
        private final String field;
        private final String before;
        private final String after;
        private final ChangeType changeType;

        public MetadataChange(String field, String before, String after, ChangeType changeType) {
            this.field = field;
            this.before = before;
            this.after = after;
            this.changeType = changeType;
        }

        public String getField() {
            return field;
        }

        public String getBefore() {
            return before;
        }

        public String getAfter() {
            return after;
        }

        public ChangeType getChangeType() {
            return changeType;
        }
    }

    public enum ChangeType {
        ADDED,
        REMOVED,
        CHANGED
    }
}
