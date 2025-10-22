package br.com.dms.domain.core;

public class DocumentType {

    private Long id;
    private String name;
    private String description;
    private Long validityInDays;
    private String requiredAttributes;

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

    public Long getValidityInDays() {
        return validityInDays;
    }

    public void setValidityInDays(Long validityInDays) {
        this.validityInDays = validityInDays;
    }

    public String getRequiredAttributes() {
        return requiredAttributes;
    }

    public void setRequiredAttributes(String requiredAttributes) {
        this.requiredAttributes = requiredAttributes;
    }
}
