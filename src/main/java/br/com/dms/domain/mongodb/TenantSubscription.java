package br.com.dms.domain.mongodb;

import br.com.dms.domain.core.BillingPlan;
import br.com.dms.domain.core.BillingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "tenantSubscription")
@CompoundIndex(def = "{'tenantId': 1}", name = "tenant_subscription_unique_idx", unique = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TenantSubscription {
    @Id
    private String id;
    private String tenantId;
    private BillingPlan plan;
    private BillingStatus status;
    private Instant trialStartedAt;
    private Instant trialEndsAt;
    private String externalSubscriptionId;
    private Instant createdAt;
    private Instant updatedAt;
}
