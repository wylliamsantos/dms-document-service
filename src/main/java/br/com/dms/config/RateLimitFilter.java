package br.com.dms.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String TENANT_CLAIM = "tenant_id";

    private static final String CRITICAL_PATH_LIMITS_DEFAULT = "/v1/documents/multipart=30,/v1/documents/base64=30,/v1/documents/presigned-url=60,/v1/billing/webhook=60";

    private final boolean enabled;
    private final int requestsPerMinute;
    private final Map<String, Integer> criticalPathLimits;
    private final CounterStore counterStore;

    @Autowired
    public RateLimitFilter(
            @Value("${dms.security.rate-limit.enabled:true}") boolean enabled,
            @Value("${dms.security.rate-limit.requests-per-minute:120}") int requestsPerMinute,
            @Value("${dms.security.rate-limit.critical-path-limits:" + CRITICAL_PATH_LIMITS_DEFAULT + "}") String criticalPathLimits,
            @Value("${dms.security.rate-limit.backend:in-memory}") String backend,
            @Value("${dms.security.rate-limit.redis-key-prefix:dms:rate-limit}") String redisKeyPrefix,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider
    ) {
        this(
            enabled,
            requestsPerMinute,
            parseCriticalPathLimits(criticalPathLimits),
            buildCounterStore(backend, redisKeyPrefix, redisTemplateProvider),
            Clock.systemUTC()
        );
    }

    RateLimitFilter(boolean enabled, int requestsPerMinute, Clock clock) {
        this(enabled, requestsPerMinute, Collections.emptyMap(), new InMemoryCounterStore(clock), clock);
    }

    RateLimitFilter(boolean enabled, int requestsPerMinute, Map<String, Integer> criticalPathLimits, Clock clock) {
        this(enabled, requestsPerMinute, criticalPathLimits, new InMemoryCounterStore(clock), clock);
    }

    RateLimitFilter(boolean enabled,
                    int requestsPerMinute,
                    Map<String, Integer> criticalPathLimits,
                    CounterStore counterStore,
                    Clock clock) {
        this.enabled = enabled;
        this.requestsPerMinute = Math.max(1, requestsPerMinute);
        this.criticalPathLimits = criticalPathLimits == null ? Collections.emptyMap() : new ConcurrentHashMap<>(criticalPathLimits);
        this.counterStore = counterStore == null ? new InMemoryCounterStore(clock) : counterStore;
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
        String path = normalizePath(request.getRequestURI());
        int limit = resolveLimitForPath(path);
        String bucketKey = resolveKey(request) + "::" + path;

        long currentCount = counterStore.increment(bucketKey, 60);
        if (currentCount > limit) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"rate_limit_exceeded\",\"message\":\"Too many requests. Please retry in about one minute.\"}");
            return;
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

    private int resolveLimitForPath(String path) {
        return criticalPathLimits.getOrDefault(path, requestsPerMinute);
    }

    private String normalizePath(String path) {
        return StringUtils.defaultIfBlank(path, "/");
    }

    private static Map<String, Integer> parseCriticalPathLimits(String raw) {
        if (StringUtils.isBlank(raw)) {
            return Collections.emptyMap();
        }
        return Arrays.stream(raw.split(","))
            .map(String::trim)
            .filter(StringUtils::isNotBlank)
            .map(entry -> entry.split("=", 2))
            .filter(parts -> parts.length == 2)
            .map(parts -> Map.entry(StringUtils.trimToEmpty(parts[0]), StringUtils.trimToEmpty(parts[1])))
            .filter(entry -> StringUtils.isNotBlank(entry.getKey()) && StringUtils.isNumeric(entry.getValue()))
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> Math.max(1, Integer.parseInt(entry.getValue())), (left, right) -> right));
    }

    private static CounterStore buildCounterStore(String backend,
                                                  String redisKeyPrefix,
                                                  ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        if ("redis".equalsIgnoreCase(StringUtils.trimToEmpty(backend))) {
            StringRedisTemplate template = redisTemplateProvider.getIfAvailable();
            if (template != null) {
                return new RedisCounterStore(template, redisKeyPrefix);
            }
        }
        return new InMemoryCounterStore(Clock.systemUTC());
    }

    interface CounterStore {
        long increment(String key, long ttlSeconds);
    }

    static class InMemoryCounterStore implements CounterStore {

        private final Clock clock;
        private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

        InMemoryCounterStore(Clock clock) {
            this.clock = clock;
        }

        @Override
        public long increment(String key, long ttlSeconds) {
            long currentWindow = clock.millis() / (Math.max(1, ttlSeconds) * 1_000);
            WindowCounter counter = counters.computeIfAbsent(key, ignored -> new WindowCounter(currentWindow));
            synchronized (counter) {
                if (counter.window() != currentWindow) {
                    counter.window = currentWindow;
                    counter.count.set(0);
                }
                return counter.count.incrementAndGet();
            }
        }
    }

    static class RedisCounterStore implements CounterStore {

        private final StringRedisTemplate redisTemplate;
        private final String keyPrefix;

        RedisCounterStore(StringRedisTemplate redisTemplate, String keyPrefix) {
            this.redisTemplate = redisTemplate;
            this.keyPrefix = StringUtils.defaultIfBlank(keyPrefix, "dms:rate-limit");
        }

        @Override
        public long increment(String key, long ttlSeconds) {
            String redisKey = keyPrefix + ":" + key;
            Long current = redisTemplate.opsForValue().increment(redisKey);
            if (current != null && current == 1L) {
                redisTemplate.expire(redisKey, Math.max(1, ttlSeconds), TimeUnit.SECONDS);
            }
            return current == null ? 1L : current;
        }
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
