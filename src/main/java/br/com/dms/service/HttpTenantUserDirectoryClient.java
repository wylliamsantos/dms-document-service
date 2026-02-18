package br.com.dms.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

@Component
@Slf4j
public class HttpTenantUserDirectoryClient implements TenantUserDirectoryClient {

    private final RestClient restClient;
    private final boolean enabled;
    private final String apiKey;
    private final String tenantUsersCountPath;

    public HttpTenantUserDirectoryClient(RestClient.Builder restClientBuilder,
                                         @Value("${dms.billing.user-directory.enabled:false}") boolean enabled,
                                         @Value("${dms.billing.user-directory.base-url:}") String baseUrl,
                                         @Value("${dms.billing.user-directory.api-key:}") String apiKey,
                                         @Value("${dms.billing.user-directory.tenant-users-count-path:/v1/tenants/{tenantId}/users/count}") String tenantUsersCountPath) {
        this.enabled = enabled;
        this.apiKey = apiKey;
        this.tenantUsersCountPath = tenantUsersCountPath;
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public Optional<Long> countActiveUsers(String tenantId) {
        if (!enabled || tenantId == null || tenantId.isBlank()) {
            return Optional.empty();
        }

        try {
            JsonNode payload = restClient.get()
                    .uri(tenantUsersCountPath, tenantId)
                    .headers(headers -> {
                        if (apiKey != null && !apiKey.isBlank()) {
                            headers.setBearerAuth(apiKey);
                        }
                    })
                    .retrieve()
                    .body(JsonNode.class);

            if (payload == null) {
                return Optional.empty();
            }

            JsonNode activeUsersNode = payload.path("activeUsers");
            if (!activeUsersNode.isNumber()) {
                return Optional.empty();
            }

            long activeUsers = activeUsersNode.asLong();
            return activeUsers < 0 ? Optional.empty() : Optional.of(activeUsers);
        } catch (RestClientException e) {
            log.warn("Failed to count active users for tenant: {}", tenantId, e);
            return Optional.empty();
        }
    }
}
