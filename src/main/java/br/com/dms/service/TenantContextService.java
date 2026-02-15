package br.com.dms.service;

import br.com.dms.exception.DmsBusinessException;
import br.com.dms.exception.TypeException;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class TenantContextService {

    private static final String TENANT_CLAIM = "tenant_id";
    private static final String TENANT_HEADER = "X-Tenant-Id";

    private final boolean allowHeaderFallback;

    public TenantContextService(@Value("${dms.tenant.allow-header-fallback:true}") boolean allowHeaderFallback) {
        this.allowHeaderFallback = allowHeaderFallback;
    }

    public String requireTenantId() {
        String fromToken = resolveFromToken();
        if (StringUtils.isNotBlank(fromToken)) {
            return fromToken;
        }

        if (allowHeaderFallback) {
            String fromHeader = resolveFromHeader();
            if (StringUtils.isNotBlank(fromHeader)) {
                return fromHeader;
            }
        }

        throw new DmsBusinessException("tenant_id não informado no token e nenhum tenant de fallback foi recebido", TypeException.VALID);
    }

    private String resolveFromToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            return StringUtils.trimToNull(jwt.getClaimAsString(TENANT_CLAIM));
        }
        return null;
    }

    private String resolveFromHeader() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        return StringUtils.trimToNull(request.getHeader(TENANT_HEADER));
    }
}
