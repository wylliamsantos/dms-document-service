package br.com.dms.service;

import br.com.dms.controller.request.BillingWebhookRequest;
import br.com.dms.domain.core.BillingPlan;
import br.com.dms.domain.core.BillingStatus;
import br.com.dms.domain.mongodb.TenantSubscription;
import br.com.dms.repository.mongo.BillingWebhookEventRepository;
import br.com.dms.repository.mongo.TenantSubscriptionRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BillingServiceTest {

    private final TenantContextService tenantContextService = mock(TenantContextService.class);
    private final TenantSubscriptionRepository tenantSubscriptionRepository = mock(TenantSubscriptionRepository.class);
    private final BillingWebhookEventRepository billingWebhookEventRepository = mock(BillingWebhookEventRepository.class);

    private final BillingService service = new BillingService(
            tenantContextService,
            tenantSubscriptionRepository,
            billingWebhookEventRepository
    );

    @Test
    void shouldCreateTrialWhenTenantHasNoSubscription() {
        when(tenantContextService.requireTenantId()).thenReturn("tenant-dev");
        when(tenantSubscriptionRepository.findByTenantId("tenant-dev")).thenReturn(Optional.empty());
        when(tenantSubscriptionRepository.save(any(TenantSubscription.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.getOrStartTrialForAuthenticatedTenant();

        assertThat(response.getTenantId()).isEqualTo("tenant-dev");
        assertThat(response.getPlan()).isEqualTo(BillingPlan.TRIAL);
        assertThat(response.getStatus()).isEqualTo(BillingStatus.TRIALING);
        assertThat(response.getTrialStartedAt()).isNotNull();
        assertThat(response.getTrialEndsAt()).isAfter(response.getTrialStartedAt());
    }

    @Test
    void shouldIgnoreDuplicateWebhookEvent() {
        var existing = TenantSubscription.builder()
                .tenantId("tenant-dev")
                .plan(BillingPlan.PRO)
                .status(BillingStatus.ACTIVE)
                .trialStartedAt(Instant.now().minusSeconds(3600))
                .trialEndsAt(Instant.now().plusSeconds(3600))
                .build();

        when(billingWebhookEventRepository.existsByEventId("evt-1")).thenReturn(true);
        when(tenantSubscriptionRepository.findByTenantId("tenant-dev")).thenReturn(Optional.of(existing));

        var response = service.applyWebhook(BillingWebhookRequest.builder()
                .eventId("evt-1")
                .eventType("subscription.updated")
                .tenantId("tenant-dev")
                .plan(BillingPlan.ENTERPRISE)
                .status(BillingStatus.CANCELED)
                .build());

        assertThat(response.getPlan()).isEqualTo(BillingPlan.PRO);
        assertThat(response.getStatus()).isEqualTo(BillingStatus.ACTIVE);
        verify(tenantSubscriptionRepository, never()).save(any());
        verify(billingWebhookEventRepository, never()).save(any());
    }

    @Test
    void shouldApplyWebhookAndPersistEventWhenEventIsNew() {
        when(billingWebhookEventRepository.existsByEventId("evt-2")).thenReturn(false);
        when(tenantSubscriptionRepository.findByTenantId("tenant-dev")).thenReturn(Optional.empty());
        when(tenantSubscriptionRepository.save(any(TenantSubscription.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.applyWebhook(BillingWebhookRequest.builder()
                .eventId("evt-2")
                .eventType("subscription.updated")
                .tenantId("tenant-dev")
                .plan(BillingPlan.STARTER)
                .status(BillingStatus.ACTIVE)
                .externalSubscriptionId("sub_123")
                .build());

        assertThat(response.getTenantId()).isEqualTo("tenant-dev");
        assertThat(response.getPlan()).isEqualTo(BillingPlan.STARTER);
        assertThat(response.getStatus()).isEqualTo(BillingStatus.ACTIVE);
        assertThat(response.getExternalSubscriptionId()).isEqualTo("sub_123");
        verify(tenantSubscriptionRepository, times(1)).save(any());
        verify(billingWebhookEventRepository, times(1)).save(any());
    }
}
