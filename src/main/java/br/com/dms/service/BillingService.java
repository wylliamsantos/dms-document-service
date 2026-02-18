package br.com.dms.service;

import br.com.dms.controller.request.BillingWebhookRequest;
import br.com.dms.controller.response.BillingSubscriptionResponse;
import br.com.dms.domain.core.BillingPlan;
import br.com.dms.domain.core.BillingStatus;
import br.com.dms.domain.mongodb.BillingWebhookEvent;
import br.com.dms.domain.mongodb.TenantSubscription;
import br.com.dms.repository.mongo.BillingWebhookEventRepository;
import br.com.dms.repository.mongo.TenantSubscriptionRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class BillingService {

    private static final long DEFAULT_TRIAL_DAYS = 14;

    private final TenantContextService tenantContextService;
    private final TenantSubscriptionRepository tenantSubscriptionRepository;
    private final BillingWebhookEventRepository billingWebhookEventRepository;
    private final BillingProviderClient billingProviderClient;

    public BillingService(TenantContextService tenantContextService,
                          TenantSubscriptionRepository tenantSubscriptionRepository,
                          BillingWebhookEventRepository billingWebhookEventRepository,
                          BillingProviderClient billingProviderClient) {
        this.tenantContextService = tenantContextService;
        this.tenantSubscriptionRepository = tenantSubscriptionRepository;
        this.billingWebhookEventRepository = billingWebhookEventRepository;
        this.billingProviderClient = billingProviderClient;
    }

    public BillingSubscriptionResponse getOrStartTrialForAuthenticatedTenant() {
        String tenantId = tenantContextService.requireTenantId();
        var subscription = tenantSubscriptionRepository.findByTenantId(tenantId)
                .orElseGet(() -> createTrialSubscription(tenantId));

        return toResponse(subscription);
    }

    public BillingSubscriptionResponse refreshSubscriptionFromProviderForAuthenticatedTenant() {
        String tenantId = tenantContextService.requireTenantId();
        var subscription = tenantSubscriptionRepository.findByTenantId(tenantId)
                .orElseGet(() -> createTrialSubscription(tenantId));

        if (subscription.getExternalSubscriptionId() == null || subscription.getExternalSubscriptionId().isBlank()) {
            return toResponse(subscription);
        }

        var providerSnapshot = billingProviderClient.fetchSubscription(subscription.getExternalSubscriptionId());
        if (providerSnapshot.isEmpty()) {
            return toResponse(subscription);
        }

        var snapshot = providerSnapshot.get();
        if (snapshot.getPlan() != null) {
            subscription.setPlan(snapshot.getPlan());
        }
        if (snapshot.getStatus() != null) {
            subscription.setStatus(snapshot.getStatus());
        }
        subscription.setUpdatedAt(Instant.now());

        return toResponse(tenantSubscriptionRepository.save(subscription));
    }

    public BillingSubscriptionResponse applyWebhook(BillingWebhookRequest request) {
        if (request.getEventId() == null || request.getEventId().isBlank()) {
            throw new IllegalArgumentException("eventId is required");
        }

        if (billingWebhookEventRepository.existsByEventId(request.getEventId())) {
            var current = tenantSubscriptionRepository.findByTenantId(request.getTenantId())
                    .orElseThrow(() -> new IllegalArgumentException("tenant subscription not found for duplicate event"));
            return toResponse(current);
        }

        var now = Instant.now();
        var subscription = tenantSubscriptionRepository.findByTenantId(request.getTenantId())
                .orElseGet(() -> TenantSubscription.builder()
                        .tenantId(request.getTenantId())
                        .createdAt(now)
                        .build());

        if (request.getPlan() != null) {
            subscription.setPlan(request.getPlan());
        }
        if (request.getStatus() != null) {
            subscription.setStatus(request.getStatus());
        }
        if (request.getExternalSubscriptionId() != null && !request.getExternalSubscriptionId().isBlank()) {
            subscription.setExternalSubscriptionId(request.getExternalSubscriptionId());
        }
        subscription.setUpdatedAt(now);

        var saved = tenantSubscriptionRepository.save(subscription);

        billingWebhookEventRepository.save(BillingWebhookEvent.builder()
                .eventId(request.getEventId())
                .tenantId(request.getTenantId())
                .eventType(request.getEventType())
                .processedAt(now)
                .build());

        return toResponse(saved);
    }

    private TenantSubscription createTrialSubscription(String tenantId) {
        var now = Instant.now();
        return tenantSubscriptionRepository.save(TenantSubscription.builder()
                .tenantId(tenantId)
                .plan(BillingPlan.TRIAL)
                .status(BillingStatus.TRIALING)
                .trialStartedAt(now)
                .trialEndsAt(now.plusSeconds(DEFAULT_TRIAL_DAYS * 24 * 60 * 60))
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private BillingSubscriptionResponse toResponse(TenantSubscription subscription) {
        return BillingSubscriptionResponse.builder()
                .tenantId(subscription.getTenantId())
                .plan(subscription.getPlan())
                .status(subscription.getStatus())
                .trialStartedAt(subscription.getTrialStartedAt())
                .trialEndsAt(subscription.getTrialEndsAt())
                .externalSubscriptionId(subscription.getExternalSubscriptionId())
                .build();
    }
}
