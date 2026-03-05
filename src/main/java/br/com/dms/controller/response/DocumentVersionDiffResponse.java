package br.com.dms.controller.response;

import java.util.List;

public class DocumentVersionDiffResponse {

    private final String documentId;
    private final String baseVersion;
    private final String targetVersion;
    private final List<MetadataChange> metadataChanges;
    private final ContentComparison contentComparison;

    public DocumentVersionDiffResponse(String documentId,
                                       String baseVersion,
                                       String targetVersion,
                                       List<MetadataChange> metadataChanges,
                                       ContentComparison contentComparison) {
        this.documentId = documentId;
        this.baseVersion = baseVersion;
        this.targetVersion = targetVersion;
        this.metadataChanges = metadataChanges;
        this.contentComparison = contentComparison;
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

    public ContentComparison getContentComparison() {
        return contentComparison;
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

    public static class ContentComparison {
        private final boolean available;
        private final String changeType;
        private final String baseSnippet;
        private final String targetSnippet;

        public ContentComparison(boolean available, String changeType, String baseSnippet, String targetSnippet) {
            this.available = available;
            this.changeType = changeType;
            this.baseSnippet = baseSnippet;
            this.targetSnippet = targetSnippet;
        }

        public boolean isAvailable() {
            return available;
        }

        public String getChangeType() {
            return changeType;
        }

        public String getBaseSnippet() {
            return baseSnippet;
        }

        public String getTargetSnippet() {
            return targetSnippet;
        }
    }

    public enum ChangeType {
        ADDED,
        REMOVED,
        CHANGED
    }
}
