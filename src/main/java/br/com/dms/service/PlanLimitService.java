package br.com.dms.service;

import br.com.dms.domain.core.BillingPlan;
import br.com.dms.domain.core.BillingStatus;
import br.com.dms.exception.DmsBusinessException;
import br.com.dms.exception.TypeException;
import br.com.dms.repository.mongo.DmsDocumentVersionRepository;
import br.com.dms.repository.mongo.TenantSubscriptionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;

@Service
public class PlanLimitService {

    private TenantContextService tenantContextService;
    private TenantSubscriptionRepository tenantSubscriptionRepository;
    private DmsDocumentVersionRepository dmsDocumentVersionRepository;
    private Clock clock;
    private boolean planLimitsEnabled;

    public PlanLimitService() {
        // for frameworks/tests that instantiate via reflection
        this.clock = Clock.systemUTC();
        this.planLimitsEnabled = true;
    }

    @Autowired
    public PlanLimitService(TenantContextService tenantContextService,
                            TenantSubscriptionRepository tenantSubscriptionRepository,
                            DmsDocumentVersionRepository dmsDocumentVersionRepository,
                            @Value("${dms.billing.plan-limits-enabled:true}") boolean planLimitsEnabled) {
        this(tenantContextService, tenantSubscriptionRepository, dmsDocumentVersionRepository, Clock.systemUTC(), planLimitsEnabled);
    }

    PlanLimitService(TenantContextService tenantContextService,
                     TenantSubscriptionRepository tenantSubscriptionRepository,
                     DmsDocumentVersionRepository dmsDocumentVersionRepository,
                     Clock clock,
                     boolean planLimitsEnabled) {
        this.tenantContextService = tenantContextService;
        this.tenantSubscriptionRepository = tenantSubscriptionRepository;
        this.dmsDocumentVersionRepository = dmsDocumentVersionRepository;
        this.clock = clock;
        this.planLimitsEnabled = planLimitsEnabled;
    }

    public void assertCanUploadDocument(String transactionId) {
        if (!planLimitsEnabled || tenantContextService == null || tenantSubscriptionRepository == null || dmsDocumentVersionRepository == null) {
            return;
        }

        String tenantId = tenantContextService.requireTenantId();

        var subscription = tenantSubscriptionRepository.findByTenantId(tenantId)
                .orElseGet(() -> br.com.dms.domain.mongodb.TenantSubscription.builder()
                        .tenantId(tenantId)
                        .plan(BillingPlan.TRIAL)
                        .status(BillingStatus.TRIALING)
                        .build());

        if (subscription.getStatus() == BillingStatus.CANCELED || subscription.getStatus() == BillingStatus.PAST_DUE) {
            throw new DmsBusinessException(
                    "Assinatura inativa para upload de documentos. Regularize a assinatura para continuar.",
                    TypeException.VALID,
                    transactionId
            );
        }

        long monthlyLimit = resolveMonthlyDocumentLimit(subscription.getPlan());
        if (monthlyLimit <= 0) {
            return;
        }

        YearMonth currentMonth = YearMonth.now(clock);
        LocalDateTime monthStart = currentMonth.atDay(1).atStartOfDay();
        LocalDateTime nextMonthStart = currentMonth.plusMonths(1).atDay(1).atStartOfDay();

        long uploadsThisMonth = dmsDocumentVersionRepository.countByTenantIdAndCreationDateGreaterThanEqualAndCreationDateLessThan(
                tenantId,
                monthStart,
                nextMonthStart
        );

        if (uploadsThisMonth >= monthlyLimit) {
            throw new DmsBusinessException(
                    String.format("Limite mensal do plano atingido (%d/%d uploads). Faça upgrade para continuar enviando documentos.", uploadsThisMonth, monthlyLimit),
                    TypeException.VALID,
                    transactionId
            );
        }
    }

    private long resolveMonthlyDocumentLimit(BillingPlan plan) {
        if (plan == null) {
            return 50L;
        }

        return switch (plan) {
            case TRIAL -> 50L;
            case STARTER -> 200L;
            case PRO -> 1000L;
            case ENTERPRISE -> -1L;
        };
    }
}
