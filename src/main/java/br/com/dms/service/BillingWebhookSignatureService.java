package br.com.dms.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class BillingWebhookSignatureService {

    private static final String HMAC_SHA_256 = "HmacSHA256";

    private final String webhookSecret;

    public BillingWebhookSignatureService(@Value("${dms.billing.webhook-secret:}") String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public void assertValidSignature(String rawPayload, String signatureHeader) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "billing webhook secret is not configured");
        }

        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "missing billing webhook signature");
        }

        var normalizedSignature = normalizeSignature(signatureHeader);
        var expectedSignature = calculateHexHmac(rawPayload);

        var receivedBytes = normalizedSignature.getBytes(StandardCharsets.UTF_8);
        var expectedBytes = expectedSignature.getBytes(StandardCharsets.UTF_8);

        if (!MessageDigest.isEqual(receivedBytes, expectedBytes)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "invalid billing webhook signature");
        }
    }

    private String normalizeSignature(String signatureHeader) {
        var trimmed = signatureHeader.trim();
        if (trimmed.startsWith("sha256=")) {
            return trimmed.substring("sha256=".length());
        }
        return trimmed;
    }

    private String calculateHexHmac(String rawPayload) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA_256));
            byte[] digest = mac.doFinal(rawPayload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "failed to validate billing webhook signature", e);
        }
    }
}
