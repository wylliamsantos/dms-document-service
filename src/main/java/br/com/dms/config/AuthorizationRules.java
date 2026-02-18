package br.com.dms.config;

public final class AuthorizationRules {

    private AuthorizationRules() {
    }

    public static final String READ = "hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN','ROLE_REVIEWER','ROLE_VIEWER','ROLE_DOCUMENT_VIEWER')";
    public static final String MANAGE = "hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')";
    public static final String REVIEW = "hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN','ROLE_REVIEWER')";
}
