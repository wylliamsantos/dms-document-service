package br.com.dms.service;

import br.com.dms.domain.core.BillingPlan;
import br.com.dms.domain.core.BillingStatus;
import br.com.dms.domain.mongodb.TenantSubscription;
import br.com.dms.exception.DmsBusinessException;
import br.com.dms.repository.mongo.DmsDocumentVersionRepository;
import br.com.dms.repository.mongo.TenantSubscriptionRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PlanLimitServiceTest {

    private final TenantContextService tenantContextService = mock(TenantContextService.class);
    private final TenantSubscriptionRepository tenantSubscriptionRepository = mock(TenantSubscriptionRepository.class);
    private final DmsDocumentVersionRepository dmsDocumentVersionRepository = mock(DmsDocumentVersionRepository.class);
    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-02-18T18:00:00Z"), ZoneOffset.UTC);

    private final PlanLimitService service = new PlanLimitService(
            tenantContextService,
            tenantSubscriptionRepository,
            dmsDocumentVersionRepository,
            fixedClock,
            true
    );

    @Test
    void shouldAllowUploadWhenBelowPlanLimit() {
        when(tenantContextService.requireTenantId()).thenReturn("tenant-dev");
        when(tenantSubscriptionRepository.findByTenantId("tenant-dev")).thenReturn(Optional.of(TenantSubscription.builder()
                .tenantId("tenant-dev")
                .plan(BillingPlan.TRIAL)
                .status(BillingStatus.TRIALING)
                .build()));
        when(dmsDocumentVersionRepository.countByTenantIdAndCreationDateGreaterThanEqualAndCreationDateLessThan(eq("tenant-dev"), any(), any()))
                .thenReturn(49L);
        when(dmsDocumentVersionRepository.sumCompletedFileSizeByTenantId("tenant-dev")).thenReturn(10L);

        assertThatCode(() -> service.assertCanUploadDocument("tx-1", 100L)).doesNotThrowAnyException();
    }

    @Test
    void shouldBlockUploadWhenMonthlyLimitIsReached() {
        when(tenantContextService.requireTenantId()).thenReturn("tenant-dev");
        when(tenantSubscriptionRepository.findByTenantId("tenant-dev")).thenReturn(Optional.of(TenantSubscription.builder()
                .tenantId("tenant-dev")
                .plan(BillingPlan.TRIAL)
                .status(BillingStatus.TRIALING)
                .build()));
        when(dmsDocumentVersionRepository.countByTenantIdAndCreationDateGreaterThanEqualAndCreationDateLessThan(eq("tenant-dev"), any(), any()))
                .thenReturn(50L);

        assertThatThrownBy(() -> service.assertCanUploadDocument("tx-2"))
                .isInstanceOf(DmsBusinessException.class)
                .hasMessageContaining("Limite mensal do plano atingido")
                .hasMessageContaining("Faça upgrade");
    }

    @Test
    void shouldAllowUnlimitedUploadsForEnterprisePlan() {
        when(tenantContextService.requireTenantId()).thenReturn("tenant-dev");
        when(tenantSubscriptionRepository.findByTenantId("tenant-dev")).thenReturn(Optional.of(TenantSubscription.builder()
                .tenantId("tenant-dev")
                .plan(BillingPlan.ENTERPRISE)
                .status(BillingStatus.ACTIVE)
                .build()));

        assertThatCode(() -> service.assertCanUploadDocument("tx-3")).doesNotThrowAnyException();
        verify(dmsDocumentVersionRepository, never())
                .countByTenantIdAndCreationDateGreaterThanEqualAndCreationDateLessThan(any(), any(), any());
    }

    @Test
    void shouldBlockUploadWhenSubscriptionIsPastDue() {
        when(tenantContextService.requireTenantId()).thenReturn("tenant-dev");
        when(tenantSubscriptionRepository.findByTenantId("tenant-dev")).thenReturn(Optional.of(TenantSubscription.builder()
                .tenantId("tenant-dev")
                .plan(BillingPlan.PRO)
                .status(BillingStatus.PAST_DUE)
                .build()));

        assertThatThrownBy(() -> service.assertCanUploadDocument("tx-4"))
                .isInstanceOf(DmsBusinessException.class)
                .hasMessageContaining("Assinatura inativa");
    }

    @Test
    void shouldBlockUploadWhenStorageLimitIsReached() {
        when(tenantContextService.requireTenantId()).thenReturn("tenant-dev");
        when(tenantSubscriptionRepository.findByTenantId("tenant-dev")).thenReturn(Optional.of(TenantSubscription.builder()
                .tenantId("tenant-dev")
                .plan(BillingPlan.TRIAL)
                .status(BillingStatus.TRIALING)
                .build()));
        when(dmsDocumentVersionRepository.countByTenantIdAndCreationDateGreaterThanEqualAndCreationDateLessThan(eq("tenant-dev"), any(), any()))
                .thenReturn(1L);

        long gb = 1024L * 1024L * 1024L;
        when(dmsDocumentVersionRepository.sumCompletedFileSizeByTenantId("tenant-dev"))
                .thenReturn(5L * gb);

        assertThatThrownBy(() -> service.assertCanUploadDocument("tx-5", 1L))
                .isInstanceOf(DmsBusinessException.class)
                .hasMessageContaining("Limite de armazenamento do plano atingido")
                .hasMessageContaining("Faça upgrade");
    }
}
