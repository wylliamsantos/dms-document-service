package br.com.dms.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Schema(name = "PayloadUrlPresigned")
public class PayloadUrlPresigned {

    private String comment;

    private boolean isFinal;

    @NotNull
    @NotBlank
    private String fileName;

    @NotNull
    @NotBlank
    private String mimeType;

    @NotNull
    private Long fileSize;

    @NotNull
    @NotBlank
    private String category;

    private String type;

    @NotNull
    @NotBlank
    private String metadata;

    @NotNull
    @NotBlank
    private String author;

    private LocalDate issuingDate;

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public LocalDate getIssuingDate() {
        return issuingDate;
    }

    public void setIssuingDate(LocalDate issuingDate) {
        this.issuingDate = issuingDate;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public boolean isFinal() {
        return isFinal;
    }

    public void setIsFinal(boolean isFinal) {
        this.isFinal = isFinal;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    @Override
    public String toString() {
        return "PayloadUrlPresigned{" +
                "comment='" + comment + '\'' +
                ", isFinal=" + isFinal +
                ", fileName='" + fileName + '\'' +
                ", mimeType='" + mimeType + '\'' +
                ", fileSize=" + fileSize +
                ", category='" + category + '\'' +
                ", type='" + type + '\'' +
                ", metadata='" + metadata + '\'' +
                ", author='" + author + '\'' +
                ", issuingDate=" + issuingDate +
                '}';
    }
}
