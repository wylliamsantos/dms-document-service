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
    private final BillingProviderClient billingProviderClient = mock(BillingProviderClient.class);

    private final BillingService service = new BillingService(
            tenantContextService,
            tenantSubscriptionRepository,
            billingWebhookEventRepository,
            billingProviderClient
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

    @Test
    void shouldRefreshSubscriptionFromProviderWhenExternalSubscriptionExists() {
        when(tenantContextService.requireTenantId()).thenReturn("tenant-dev");
        when(tenantSubscriptionRepository.findByTenantId("tenant-dev")).thenReturn(Optional.of(TenantSubscription.builder()
                .tenantId("tenant-dev")
                .plan(BillingPlan.STARTER)
                .status(BillingStatus.PAST_DUE)
                .externalSubscriptionId("sub_123")
                .build()));
        when(billingProviderClient.fetchSubscription("sub_123")).thenReturn(Optional.of(BillingProviderSubscriptionSnapshot.builder()
                .externalSubscriptionId("sub_123")
                .plan(BillingPlan.PRO)
                .status(BillingStatus.ACTIVE)
                .build()));
        when(tenantSubscriptionRepository.save(any(TenantSubscription.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.refreshSubscriptionFromProviderForAuthenticatedTenant();

        assertThat(response.getTenantId()).isEqualTo("tenant-dev");
        assertThat(response.getPlan()).isEqualTo(BillingPlan.PRO);
        assertThat(response.getStatus()).isEqualTo(BillingStatus.ACTIVE);
        verify(tenantSubscriptionRepository, times(1)).save(any(TenantSubscription.class));
    }

    @Test
    void shouldSkipProviderRefreshWhenNoExternalSubscriptionExists() {
        when(tenantContextService.requireTenantId()).thenReturn("tenant-dev");
        when(tenantSubscriptionRepository.findByTenantId("tenant-dev")).thenReturn(Optional.of(TenantSubscription.builder()
                .tenantId("tenant-dev")
                .plan(BillingPlan.TRIAL)
                .status(BillingStatus.TRIALING)
                .build()));

        var response = service.refreshSubscriptionFromProviderForAuthenticatedTenant();

        assertThat(response.getTenantId()).isEqualTo("tenant-dev");
        assertThat(response.getPlan()).isEqualTo(BillingPlan.TRIAL);
        assertThat(response.getStatus()).isEqualTo(BillingStatus.TRIALING);
        verifyNoInteractions(billingProviderClient);
        verify(tenantSubscriptionRepository, never()).save(any(TenantSubscription.class));
    }

    @Test
    void shouldSupportUpgradeAndDowngradeLifecycleViaWebhook() {
        var existing = TenantSubscription.builder()
                .tenantId("tenant-dev")
                .plan(BillingPlan.STARTER)
                .status(BillingStatus.ACTIVE)
                .externalSubscriptionId("sub_123")
                .build();

        when(billingWebhookEventRepository.existsByEventId("evt-upgrade")).thenReturn(false);
        when(billingWebhookEventRepository.existsByEventId("evt-downgrade")).thenReturn(false);
        when(tenantSubscriptionRepository.findByTenantId("tenant-dev")).thenReturn(Optional.of(existing));
        when(tenantSubscriptionRepository.save(any(TenantSubscription.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var upgraded = service.applyWebhook(BillingWebhookRequest.builder()
                .eventId("evt-upgrade")
                .eventType("subscription.updated")
                .tenantId("tenant-dev")
                .plan(BillingPlan.PRO)
                .status(BillingStatus.ACTIVE)
                .externalSubscriptionId("sub_123")
                .build());

        var downgraded = service.applyWebhook(BillingWebhookRequest.builder()
                .eventId("evt-downgrade")
                .eventType("subscription.updated")
                .tenantId("tenant-dev")
                .plan(BillingPlan.STARTER)
                .status(BillingStatus.PAST_DUE)
                .externalSubscriptionId("sub_123")
                .build());

        assertThat(upgraded.getPlan()).isEqualTo(BillingPlan.PRO);
        assertThat(upgraded.getStatus()).isEqualTo(BillingStatus.ACTIVE);
        assertThat(downgraded.getPlan()).isEqualTo(BillingPlan.STARTER);
        assertThat(downgraded.getStatus()).isEqualTo(BillingStatus.PAST_DUE);
        verify(tenantSubscriptionRepository, times(2)).save(any());
        verify(billingWebhookEventRepository, times(2)).save(any());
    }
}
