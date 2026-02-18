package br.com.dms.service;

import br.com.dms.domain.core.BillingPlan;
import br.com.dms.domain.core.BillingStatus;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class BillingProviderSubscriptionSnapshot {
    String externalSubscriptionId;
    BillingPlan plan;
    BillingStatus status;
}
