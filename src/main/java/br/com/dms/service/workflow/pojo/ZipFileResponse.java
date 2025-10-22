package br.com.dms.service.workflow.pojo;

import java.util.zip.ZipEntry;

public class ZipFileResponse {
    private ZipEntry zipEntry;
    private String base64String;

    public ZipEntry getZipEntry() {
        return zipEntry;
    }

    public void setZipEntry(ZipEntry zipEntry) {
        this.zipEntry = zipEntry;
    }

    public String getBase64String() {
        return base64String;
    }

    public void setBase64String(String base64String) {
        this.base64String = base64String;
    }
}
