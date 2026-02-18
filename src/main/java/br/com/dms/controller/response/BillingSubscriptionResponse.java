package br.com.dms.controller.response;

import br.com.dms.domain.core.BillingPlan;
import br.com.dms.domain.core.BillingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BillingSubscriptionResponse {
    private String tenantId;
    private BillingPlan plan;
    private BillingStatus status;
    private Instant trialStartedAt;
    private Instant trialEndsAt;
    private String externalSubscriptionId;
}
