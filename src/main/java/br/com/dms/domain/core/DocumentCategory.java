package br.com.dms.domain.core;

public class DocumentCategory {

    private Long id;
    private String name;
    private String description;
    private String prefix;
    private String mainType;
    private String typeSearch;
    private String uniqueAttributes;
    private String searchDuplicateCriteria;
    private String path;
    private Long validityInDays;
    private DocumentGroup documentGroup;
    private DocumentType documentType;
    private String site;
    private String parentFolder;

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

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getMainType() {
        return mainType;
    }

    public void setMainType(String mainType) {
        this.mainType = mainType;
    }

    public String getTypeSearch() {
        return typeSearch;
    }

    public void setTypeSearch(String typeSearch) {
        this.typeSearch = typeSearch;
    }

    public String getUniqueAttributes() {
        return uniqueAttributes;
    }

    public void setUniqueAttributes(String uniqueAttributes) {
        this.uniqueAttributes = uniqueAttributes;
    }

    public String getSearchDuplicateCriteria() {
        return searchDuplicateCriteria;
    }

    public void setSearchDuplicateCriteria(String searchDuplicateCriteria) {
        this.searchDuplicateCriteria = searchDuplicateCriteria;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
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

    public String getSite() {
        return site;
    }

    public void setSite(String site) {
        this.site = site;
    }

    public String getParentFolder() {
        return parentFolder;
    }

    public void setParentFolder(String parentFolder) {
        this.parentFolder = parentFolder;
    }

    public Long getConditionalValidityInDays() {
        if (getDocumentType() != null && getDocumentType().getValidityInDays() != null) {
            return getDocumentType().getValidityInDays();
        }
        return getValidityInDays();
    }
}
