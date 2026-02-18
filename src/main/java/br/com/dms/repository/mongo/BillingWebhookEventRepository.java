package br.com.dms.repository.mongo;

import br.com.dms.domain.mongodb.BillingWebhookEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BillingWebhookEventRepository extends MongoRepository<BillingWebhookEvent, String> {

    boolean existsByEventId(String eventId);
}
