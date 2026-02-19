package br.com.dms.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.FilterChain;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObservabilityFilterTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ObservabilityFilter filter = new ObservabilityFilter(meterRegistry);

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        MDC.clear();
        meterRegistry.clear();
    }

    @Test
    void shouldPropagateTraceIdAndTenantFromJwtAndRecordLatencyMetric() throws Exception {
        Jwt jwt = new Jwt(
                "token",
                null,
                null,
                Map.of("alg", "none"),
                Map.of("tenant_id", "tenant-jwt")
        );
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt, null));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/categories/all");
        request.addHeader("X-Trace-Id", "trace-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/v1/categories/all");
            ((MockHttpServletResponse) res).setStatus(200);
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Trace-Id")).isEqualTo("trace-123");
        assertThat(meterRegistry.find("dms.http.server.latency")
                .tag("tenant", "tenant-jwt")
                .tag("status", "200")
                .tag("uri", "/v1/categories/all")
                .timer())
                .isNotNull();
        assertThat(MDC.get("traceId")).isNull();
        assertThat(MDC.get("tenantId")).isNull();
    }

    @Test
    void shouldFallbackToTenantHeaderWhenJwtIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/documents/multipart");
        request.addHeader("X-Tenant-Id", "tenant-header");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/v1/documents/multipart");
            ((MockHttpServletResponse) res).setStatus(201);
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Trace-Id")).isNotBlank();
        assertThat(meterRegistry.find("dms.http.server.latency")
                .tag("tenant", "tenant-header")
                .tag("status", "201")
                .tag("uri", "/v1/documents/multipart")
                .timer())
                .isNotNull();
    }

    @Test
    void shouldIncrementErrorCounterWhenFilterChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/failure");
        request.addHeader("X-Tenant-Id", "tenant-error");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/v1/failure");
            throw new IllegalStateException("boom");
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("boom");

        assertThat(meterRegistry.find("dms.http.server.errors")
                .tag("tenant", "tenant-error")
                .tag("uri", "/v1/failure")
                .tag("exception", "IllegalStateException")
                .counter())
                .isNotNull();
        assertThat(meterRegistry.find("dms.http.server.errors")
                .tag("tenant", "tenant-error")
                .tag("uri", "/v1/failure")
                .tag("exception", "IllegalStateException")
                .counter()
                .count())
                .isEqualTo(1.0);
    }
}
