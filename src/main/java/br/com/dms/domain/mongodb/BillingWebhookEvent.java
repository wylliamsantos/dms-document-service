package br.com.dms.domain.mongodb;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "billingWebhookEvent")
@CompoundIndex(def = "{'eventId': 1}", name = "billing_webhook_event_unique_idx", unique = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BillingWebhookEvent {
    @Id
    private String id;
    private String eventId;
    private String tenantId;
    private String eventType;
    private Instant processedAt;
}
