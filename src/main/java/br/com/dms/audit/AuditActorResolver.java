package br.com.dms.audit;

import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class AuditActorResolver {

    public String resolveUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return "system";
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            String preferred = StringUtils.trimToNull(jwt.getClaimAsString("preferred_username"));
            if (preferred != null) return preferred;
            String sub = StringUtils.trimToNull(jwt.getSubject());
            if (sub != null) return sub;
        }

        String name = StringUtils.trimToNull(authentication.getName());
        return name == null ? "system" : name;
    }
}
