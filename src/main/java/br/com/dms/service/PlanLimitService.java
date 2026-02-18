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
    private TenantUserDirectoryClient tenantUserDirectoryClient;
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
                            TenantUserDirectoryClient tenantUserDirectoryClient,
                            @Value("${dms.billing.plan-limits-enabled:true}") boolean planLimitsEnabled) {
        this(tenantContextService, tenantSubscriptionRepository, dmsDocumentVersionRepository, tenantUserDirectoryClient, Clock.systemUTC(), planLimitsEnabled);
    }

    PlanLimitService(TenantContextService tenantContextService,
                     TenantSubscriptionRepository tenantSubscriptionRepository,
                     DmsDocumentVersionRepository dmsDocumentVersionRepository,
                     TenantUserDirectoryClient tenantUserDirectoryClient,
                     Clock clock,
                     boolean planLimitsEnabled) {
        this.tenantContextService = tenantContextService;
        this.tenantSubscriptionRepository = tenantSubscriptionRepository;
        this.dmsDocumentVersionRepository = dmsDocumentVersionRepository;
        this.tenantUserDirectoryClient = tenantUserDirectoryClient;
        this.clock = clock;
        this.planLimitsEnabled = planLimitsEnabled;
    }

    public void assertCanUploadDocument(String transactionId) {
        assertCanUploadDocument(transactionId, null);
    }

    public void assertCanUploadDocument(String transactionId, Long incomingFileSizeBytes) {
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

        assertCanAddUserForSubscription(subscription, tenantId, transactionId);

        long monthlyLimit = resolveMonthlyDocumentLimit(subscription.getPlan());
        if (monthlyLimit > 0) {
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

        long storageLimitBytes = resolveStorageLimitBytes(subscription.getPlan());
        if (storageLimitBytes <= 0) {
            return;
        }

        Long currentStorageQuery = dmsDocumentVersionRepository.sumCompletedFileSizeByTenantId(tenantId);
        long currentStorageBytes = currentStorageQuery == null ? 0L : currentStorageQuery;
        long incomingBytes = incomingFileSizeBytes == null ? 0L : Math.max(incomingFileSizeBytes, 0L);
        long projectedStorage = currentStorageBytes + incomingBytes;

        if (projectedStorage > storageLimitBytes) {
            throw new DmsBusinessException(
                    String.format(
                            "Limite de armazenamento do plano atingido (%s/%s). Faça upgrade para continuar enviando documentos.",
                            humanReadableBytes(projectedStorage),
                            humanReadableBytes(storageLimitBytes)
                    ),
                    TypeException.VALID,
                    transactionId
            );
        }
    }

    public void assertCanProvisionUser(String transactionId) {
        if (!planLimitsEnabled || tenantContextService == null || tenantSubscriptionRepository == null) {
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
                    "Assinatura inativa para provisionamento de usuários. Regularize a assinatura para continuar.",
                    TypeException.VALID,
                    transactionId
            );
        }

        assertCanAddUserForSubscription(subscription, tenantId, transactionId);
    }

    private void assertCanAddUserForSubscription(br.com.dms.domain.mongodb.TenantSubscription subscription,
                                                 String tenantId,
                                                 String transactionId) {
        long userLimit = resolveUserLimit(subscription.getPlan());
        if (userLimit <= 0 || tenantUserDirectoryClient == null) {
            return;
        }

        var activeUsers = tenantUserDirectoryClient.countActiveUsers(tenantId);
        if (activeUsers.isPresent() && activeUsers.get() >= userLimit) {
            throw new DmsBusinessException(
                    String.format("Limite de usuários ativos do plano atingido (%d/%d). Faça upgrade para adicionar novos usuários.", activeUsers.get(), userLimit),
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

    private long resolveUserLimit(BillingPlan plan) {
        if (plan == null) {
            return 3L;
        }

        return switch (plan) {
            case TRIAL -> 3L;
            case STARTER -> 10L;
            case PRO -> 50L;
            case ENTERPRISE -> -1L;
        };
    }

    private long resolveStorageLimitBytes(BillingPlan plan) {
        long gb = 1024L * 1024L * 1024L;
        if (plan == null) {
            return 5L * gb;
        }

        return switch (plan) {
            case TRIAL -> 5L * gb;
            case STARTER -> 20L * gb;
            case PRO -> 100L * gb;
            case ENTERPRISE -> -1L;
        };
    }

    private String humanReadableBytes(long bytes) {
        long gb = 1024L * 1024L * 1024L;
        long mb = 1024L * 1024L;
        if (bytes >= gb) {
            return String.format("%.2f GB", (double) bytes / gb);
        }
        return String.format("%.2f MB", (double) bytes / mb);
    }
}
