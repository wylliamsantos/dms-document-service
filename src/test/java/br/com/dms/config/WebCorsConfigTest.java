package br.com.dms.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebCorsConfigTest {

    @Test
    void shouldFailInProdWhenWildcardOriginIsConfigured() {
        assertThatThrownBy(() -> new WebCorsConfig(
            "*",
            "GET,POST",
            "*",
            true,
            "prod",
            true
        )).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Insecure CORS configuration");
    }

    @Test
    void shouldFailInProdWhenLocalhostOriginIsConfigured() {
        assertThatThrownBy(() -> new WebCorsConfig(
            "http://localhost:5173",
            "GET,POST",
            "*",
            true,
            "prod",
            true
        )).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Insecure CORS configuration");
    }

    @Test
    void shouldAllowProdWhenOriginsAreTrusted() {
        assertThatCode(() -> new WebCorsConfig(
            "https://app.dms.com",
            "GET,POST",
            "Authorization,Content-Type",
            true,
            "prod",
            true
        )).doesNotThrowAnyException();
    }

    @Test
    void shouldAllowInsecureOriginsOutsideProd() {
        assertThatCode(() -> new WebCorsConfig(
            "*",
            "GET,POST",
            "*",
            true,
            "dev",
            true
        )).doesNotThrowAnyException();
    }
}
