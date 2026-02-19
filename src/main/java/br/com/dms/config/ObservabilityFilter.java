package br.com.dms.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@Component
public class ObservabilityFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityFilter.class);
    private static final String TRACE_HEADER = "X-Trace-Id";
    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String TENANT_CLAIM = "tenant_id";

    private final MeterRegistry meterRegistry;

    public ObservabilityFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startNanos = System.nanoTime();
        String traceId = resolveTraceId(request);
        String tenantId = resolveTenantId(request);

        MDC.put("traceId", traceId);
        MDC.put("tenantId", StringUtils.defaultIfBlank(tenantId, "unknown"));
        response.setHeader(TRACE_HEADER, traceId);

        Exception error = null;
        try {
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            error = ex;
            throw ex;
        } finally {
            long elapsedNanos = System.nanoTime() - startNanos;
            recordMetrics(request, response, tenantId, error, elapsedNanos);
            logRequest(request, response, tenantId, traceId, elapsedNanos, error);
            MDC.remove("traceId");
            MDC.remove("tenantId");
        }
    }

    private void recordMetrics(HttpServletRequest request,
                               HttpServletResponse response,
                               String tenantId,
                               Exception error,
                               long elapsedNanos) {
        String method = request.getMethod();
        String uri = resolveUriPattern(request);
        String status = String.valueOf(response.getStatus());
        String exception = error == null ? "none" : error.getClass().getSimpleName();
        String tenantTag = StringUtils.defaultIfBlank(tenantId, "unknown");
        String outcome = response.getStatus() >= 500 || error != null ? "SERVER_ERROR"
            : response.getStatus() >= 400 ? "CLIENT_ERROR"
            : "SUCCESS";

        Timer.builder("dms.http.server.latency")
            .description("HTTP request latency by tenant and endpoint")
            .tag("method", method)
            .tag("uri", uri)
            .tag("status", status)
            .tag("outcome", outcome)
            .tag("tenant", tenantTag)
            .tag("exception", exception)
            .register(meterRegistry)
            .record(Duration.ofNanos(elapsedNanos));

        if (response.getStatus() >= 500 || error != null) {
            Counter.builder("dms.http.server.errors")
                .description("HTTP server errors by tenant and endpoint")
                .tag("method", method)
                .tag("uri", uri)
                .tag("status", status)
                .tag("tenant", tenantTag)
                .tag("exception", exception)
                .register(meterRegistry)
                .increment();
        }
    }

    private void logRequest(HttpServletRequest request,
                            HttpServletResponse response,
                            String tenantId,
                            String traceId,
                            long elapsedNanos,
                            Exception error) {
        long elapsedMs = Duration.ofNanos(elapsedNanos).toMillis();
        String uri = resolveUriPattern(request);

        log.info("event=http_request method={} uri={} status={} duration_ms={} tenant_id={} trace_id={} error={}",
            request.getMethod(),
            uri,
            response.getStatus(),
            elapsedMs,
            StringUtils.defaultIfBlank(tenantId, "unknown"),
            traceId,
            error == null ? "none" : error.getClass().getSimpleName()
        );
    }

    private String resolveTraceId(HttpServletRequest request) {
        return StringUtils.defaultIfBlank(request.getHeader(TRACE_HEADER), UUID.randomUUID().toString());
    }

    private String resolveTenantId(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            String tenantFromToken = StringUtils.trimToNull(jwt.getClaimAsString(TENANT_CLAIM));
            if (tenantFromToken != null) {
                return tenantFromToken;
            }
        }

        return StringUtils.trimToNull(request.getHeader(TENANT_HEADER));
    }

    private String resolveUriPattern(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern != null) {
            return pattern.toString();
        }
        return request.getRequestURI();
    }
}
