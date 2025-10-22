package br.com.dms.service.dto;

public record DocumentContent(byte[] content, String mimeType) {

    public static DocumentContent empty() {
        return new DocumentContent(new byte[0], null);
    }
}

