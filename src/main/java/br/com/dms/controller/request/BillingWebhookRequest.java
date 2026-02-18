package br.com.dms.controller.request;

import br.com.dms.domain.core.BillingPlan;
import br.com.dms.domain.core.BillingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BillingWebhookRequest {
    private String eventId;
    private String eventType;
    private String tenantId;
    private BillingPlan plan;
    private BillingStatus status;
    private String externalSubscriptionId;
}
