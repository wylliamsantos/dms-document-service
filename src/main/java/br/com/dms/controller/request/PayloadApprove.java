package br.com.dms.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PayloadApprove")
public class PayloadApprove {
    private String signatureText;

    public String getSignatureText() {
        return signatureText;
    }

    public void setSignatureText(String signatureText) {
        this.signatureText = signatureText;
    }

    @Override
    public String toString() {
        return "PayloadApprove{" +
                "signatureText='" + signatureText + '\'' +
                '}';
    }
}
