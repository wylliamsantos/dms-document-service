package br.com.dms.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String TENANT_CLAIM = "tenant_id";

    private final boolean enabled;
    private final int requestsPerMinute;
    private final Clock clock;
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Autowired
    public RateLimitFilter(
            @Value("${dms.security.rate-limit.enabled:true}") boolean enabled,
            @Value("${dms.security.rate-limit.requests-per-minute:120}") int requestsPerMinute
    ) {
        this(enabled, requestsPerMinute, Clock.systemUTC());
    }

    RateLimitFilter(boolean enabled, int requestsPerMinute, Clock clock) {
        this.enabled = enabled;
        this.requestsPerMinute = Math.max(1, requestsPerMinute);
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !enabled || uri.startsWith("/actuator") || uri.startsWith("/swagger-ui") || uri.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String key = resolveKey(request);
        long currentWindow = clock.millis() / 60_000;

        WindowCounter counter = counters.computeIfAbsent(key, ignored -> new WindowCounter(currentWindow));
        synchronized (counter) {
            if (counter.window() != currentWindow) {
                counter.window = currentWindow;
                counter.count.set(0);
            }

            int currentCount = counter.count.incrementAndGet();
            if (currentCount > requestsPerMinute) {
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"rate_limit_exceeded\",\"message\":\"Too many requests. Please retry in about one minute.\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveKey(HttpServletRequest request) {
        String tenantId = resolveTenantId(request);
        if (tenantId != null) {
            return "tenant:" + tenantId;
        }
        String ip = StringUtils.defaultIfBlank(request.getRemoteAddr(), "unknown");
        return "ip:" + ip;
    }

    private String resolveTenantId(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            String tenant = StringUtils.trimToNull(jwt.getClaimAsString(TENANT_CLAIM));
            if (tenant != null) {
                return tenant;
            }
        }

        return StringUtils.trimToNull(request.getHeader(TENANT_HEADER));
    }

    private static class WindowCounter {
        private long window;
        private final AtomicInteger count = new AtomicInteger(0);

        private WindowCounter(long window) {
            this.window = window;
        }

        long window() {
            return window;
        }
    }
}
