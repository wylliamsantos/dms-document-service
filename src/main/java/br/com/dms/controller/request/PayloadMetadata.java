package br.com.dms.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PayloadMetadata")
public class PayloadMetadata {

    private String fileName;
    private String properties;

    public String getProperties() {
        return properties;
    }

    public void setProperties(String properties) {
        this.properties = properties;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    @Override
    public String toString() {
        return "PayloadMetadata{" +
                "fileName='" + fileName + '\'' +
                ", properties='" + properties + '\'' +
                '}';
    }
}
