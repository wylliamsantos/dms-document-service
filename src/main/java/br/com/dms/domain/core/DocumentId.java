package br.com.dms.domain.core;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.ToString;

import java.io.Serializable;

@Schema(description = "Representa o identificador do documento com ID e versão.")
@ToString
public class DocumentId implements Serializable {

    private static final long serialVersionUID = 1L;

    public DocumentId(String id, String version) {
        this.id = id;
        this.version = version;
    }

    public DocumentId() {
    }

    @Schema(description = "Identificador único do documento", example = "12345")
    private String id;

    @Schema(description = "Versão do documento", example = "1.0")
    private String version;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

}
