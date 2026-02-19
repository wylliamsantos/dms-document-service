package br.com.dms.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

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
}
