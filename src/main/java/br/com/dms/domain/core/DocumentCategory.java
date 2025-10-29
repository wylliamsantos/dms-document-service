package br.com.dms.domain.core;

public class DocumentCategory {

    private Long id;
    private String name;
    private String description;
    private String uniqueAttributes;
    private Long validityInDays;
    private DocumentGroup documentGroup;
    private DocumentType documentType;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUniqueAttributes() {
        return uniqueAttributes;
    }

    public void setUniqueAttributes(String uniqueAttributes) {
        this.uniqueAttributes = uniqueAttributes;
    }

    public Long getValidityInDays() {
        return validityInDays;
    }

    public void setValidityInDays(Long validityInDays) {
        this.validityInDays = validityInDays;
    }

    public DocumentGroup getDocumentGroup() {
        return documentGroup;
    }

    public void setDocumentGroup(DocumentGroup documentGroup) {
        this.documentGroup = documentGroup;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(DocumentType documentType) {
        this.documentType = documentType;
    }

    public Long getConditionalValidityInDays() {
        if (getDocumentType() != null && getDocumentType().getValidityInDays() != null) {
            return getDocumentType().getValidityInDays();
        }
        return getValidityInDays();
    }
}
