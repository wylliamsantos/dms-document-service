package br.com.dms.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RateLimitFilterTest {

    @Test
    void shouldBlockWhenLimitIsExceededWithinSameMinute() throws Exception {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-02-19T05:00:00Z"), ZoneOffset.UTC);
        RateLimitFilter filter = new RateLimitFilter(true, 2, fixedClock);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/categories/all");
        request.setRemoteAddr("127.0.0.1");

        MockHttpServletResponse response1 = new MockHttpServletResponse();
        filter.doFilter(request, response1, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));
        assertThat(response1.getStatus()).isEqualTo(200);

        MockHttpServletResponse response2 = new MockHttpServletResponse();
        filter.doFilter(request, response2, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));
        assertThat(response2.getStatus()).isEqualTo(200);

        MockHttpServletResponse response3 = new MockHttpServletResponse();
        filter.doFilter(request, response3, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));
        assertThat(response3.getStatus()).isEqualTo(429);
        assertThat(response3.getContentAsString()).contains("rate_limit_exceeded");
    }

    @Test
    void shouldIgnoreActuatorEndpoints() throws Exception {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-02-19T05:00:00Z"), ZoneOffset.UTC);
        RateLimitFilter filter = new RateLimitFilter(true, 1, fixedClock);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        request.setRemoteAddr("127.0.0.1");

        MockHttpServletResponse response1 = new MockHttpServletResponse();
        filter.doFilter(request, response1, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));
        assertThat(response1.getStatus()).isEqualTo(200);

        MockHttpServletResponse response2 = new MockHttpServletResponse();
        filter.doFilter(request, response2, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));
        assertThat(response2.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldApplyStricterLimitsForCriticalEndpointsOnly() throws Exception {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-02-19T05:00:00Z"), ZoneOffset.UTC);
        RateLimitFilter filter = new RateLimitFilter(true, 5, Map.of("/v1/documents/multipart", 2), fixedClock);

        MockHttpServletRequest criticalRequest = new MockHttpServletRequest("POST", "/v1/documents/multipart");
        criticalRequest.setRemoteAddr("127.0.0.1");

        MockHttpServletResponse critical1 = new MockHttpServletResponse();
        filter.doFilter(criticalRequest, critical1, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));
        assertThat(critical1.getStatus()).isEqualTo(200);

        MockHttpServletResponse critical2 = new MockHttpServletResponse();
        filter.doFilter(criticalRequest, critical2, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));
        assertThat(critical2.getStatus()).isEqualTo(200);

        MockHttpServletResponse critical3 = new MockHttpServletResponse();
        filter.doFilter(criticalRequest, critical3, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));
        assertThat(critical3.getStatus()).isEqualTo(429);

        MockHttpServletRequest nonCriticalRequest = new MockHttpServletRequest("GET", "/v1/categories/all");
        nonCriticalRequest.setRemoteAddr("127.0.0.1");

        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse allowed = new MockHttpServletResponse();
            filter.doFilter(nonCriticalRequest, allowed, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));
            assertThat(allowed.getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(nonCriticalRequest, blocked, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));
        assertThat(blocked.getStatus()).isEqualTo(429);
    }

    @Test
    void shouldUseRedisStoreWithTtlOnFirstIncrement() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

        when(template.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("dms:rate-limit:tenant:t1::/v1/documents/multipart")).thenReturn(1L, 2L);

        RateLimitFilter.RedisCounterStore store = new RateLimitFilter.RedisCounterStore(template, "dms:rate-limit");

        long first = store.increment("tenant:t1::/v1/documents/multipart", 60);
        long second = store.increment("tenant:t1::/v1/documents/multipart", 60);

        assertThat(first).isEqualTo(1L);
        assertThat(second).isEqualTo(2L);
        verify(template, times(1)).expire("dms:rate-limit:tenant:t1::/v1/documents/multipart", 60, TimeUnit.SECONDS);
    }
}
