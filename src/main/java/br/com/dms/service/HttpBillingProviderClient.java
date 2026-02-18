package br.com.dms.service;

import br.com.dms.domain.core.BillingPlan;
import br.com.dms.domain.core.BillingStatus;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Locale;
import java.util.Optional;

@Component
@Slf4j
public class HttpBillingProviderClient implements BillingProviderClient {

    private final RestClient restClient;
    private final boolean enabled;
    private final String apiKey;
    private final String subscriptionPath;

    public HttpBillingProviderClient(RestClient.Builder restClientBuilder,
                                     @Value("${dms.billing.provider.enabled:false}") boolean enabled,
                                     @Value("${dms.billing.provider.base-url:}") String baseUrl,
                                     @Value("${dms.billing.provider.api-key:}") String apiKey,
                                     @Value("${dms.billing.provider.subscription-path:/v1/subscriptions/{id}}") String subscriptionPath) {
        this.enabled = enabled;
        this.apiKey = apiKey;
        this.subscriptionPath = subscriptionPath;
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public Optional<BillingProviderSubscriptionSnapshot> fetchSubscription(String externalSubscriptionId) {
        if (!enabled) {
            return Optional.empty();
        }

        if (externalSubscriptionId == null || externalSubscriptionId.isBlank()) {
            return Optional.empty();
        }

        try {
            JsonNode payload = restClient.get()
                    .uri(subscriptionPath, externalSubscriptionId)
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

            return Optional.of(BillingProviderSubscriptionSnapshot.builder()
                    .externalSubscriptionId(externalSubscriptionId)
                    .plan(parsePlan(payload.path("plan").asText(null)))
                    .status(parseStatus(payload.path("status").asText(null)))
                    .build());
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("Failed to fetch billing provider subscription: {}", externalSubscriptionId, e);
            return Optional.empty();
        }
    }

    private BillingPlan parsePlan(String rawPlan) {
        if (rawPlan == null || rawPlan.isBlank()) {
            return null;
        }
        return BillingPlan.valueOf(rawPlan.trim().toUpperCase(Locale.ROOT));
    }

    private BillingStatus parseStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return null;
        }
        return BillingStatus.valueOf(rawStatus.trim().toUpperCase(Locale.ROOT));
    }
}
