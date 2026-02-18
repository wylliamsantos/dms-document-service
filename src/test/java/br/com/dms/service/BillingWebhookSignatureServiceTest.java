package br.com.dms.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BillingWebhookSignatureServiceTest {

    private static final String SECRET = "super-secret";
    private static final String PAYLOAD = "{\"eventId\":\"evt-1\",\"tenantId\":\"tenant-dev\"}";
    private static final String SIGNATURE_HEX = "4546b7ae8a56cfa3152eb77f77f160d60741b4bda0b33e4c437673ec4f995eda";

    private final BillingWebhookSignatureService service = new BillingWebhookSignatureService(SECRET);

    @Test
    void shouldAcceptValidHexSignature() {
        assertThatCode(() -> service.assertValidSignature(PAYLOAD, SIGNATURE_HEX))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptSha256PrefixedSignature() {
        assertThatCode(() -> service.assertValidSignature(PAYLOAD, "sha256=" + SIGNATURE_HEX))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectInvalidSignature() {
        assertThatThrownBy(() -> service.assertValidSignature(PAYLOAD, "sha256=invalid"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401 UNAUTHORIZED");
    }

    @Test
    void shouldRejectWhenSecretIsMissing() {
        var serviceWithoutSecret = new BillingWebhookSignatureService(" ");

        assertThatThrownBy(() -> serviceWithoutSecret.assertValidSignature(PAYLOAD, SIGNATURE_HEX))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("500 INTERNAL_SERVER_ERROR");
    }
}
