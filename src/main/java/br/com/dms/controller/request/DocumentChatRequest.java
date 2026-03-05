package br.com.dms.controller.request;

import jakarta.validation.constraints.NotBlank;

public class DocumentChatRequest {

    @NotBlank
    private String message;

    private String version;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
