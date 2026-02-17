package br.com.dms.service;

import br.com.dms.exception.DmsBusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TenantContextServiceTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldResolveTenantFromJwtClaim() {
        TenantContextService service = new TenantContextService(false);
        setJwtTenant("tenant-token");

        String tenantId = service.requireTenantId();

        assertEquals("tenant-token", tenantId);
    }

    @Test
    void shouldUseHeaderFallbackWhenEnabledAndTokenMissing() {
        TenantContextService service = new TenantContextService(true);
        setRequestHeader("X-Tenant-Id", "tenant-header");

        String tenantId = service.requireTenantId();

        assertEquals("tenant-header", tenantId);
    }

    @Test
    void shouldThrowWhenHeaderFallbackDisabledAndTokenMissing() {
        TenantContextService service = new TenantContextService(false);
        setRequestHeader("X-Tenant-Id", "tenant-header");

        assertThrows(DmsBusinessException.class, service::requireTenantId);
    }

    @Test
    void shouldPreferTokenOverHeaderWhenBothArePresent() {
        TenantContextService service = new TenantContextService(true);
        setJwtTenant("tenant-token");
        setRequestHeader("X-Tenant-Id", "tenant-header");

        String tenantId = service.requireTenantId();

        assertEquals("tenant-token", tenantId);
    }

    private void setJwtTenant(String tenantId) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("tenant_id", tenantId)
                .build();

        var authentication = new UsernamePasswordAuthenticationToken(jwt, null);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void setRequestHeader(String headerName, String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(headerName, value);
        ServletRequestAttributes attributes = new ServletRequestAttributes((HttpServletRequest) request);
        RequestContextHolder.setRequestAttributes(attributes);
    }
}
