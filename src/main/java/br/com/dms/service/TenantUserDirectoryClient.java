package br.com.dms.service;

import java.util.Optional;

public interface TenantUserDirectoryClient {

    Optional<Long> countActiveUsers(String tenantId);
}
