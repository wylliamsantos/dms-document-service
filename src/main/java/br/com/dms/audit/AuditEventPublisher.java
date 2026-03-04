package br.com.dms.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AuditEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AuditEventPublisher.class);

    private final AmqpTemplate amqpTemplate;
    private final boolean enabled;
    private final String exchange;
    private final String routingKey;

    public AuditEventPublisher(AmqpTemplate amqpTemplate,
                               @Value("${dms.audit.enabled:true}") boolean enabled,
                               @Value("${dms.audit.exchange:audit.events}") String exchange,
                               @Value("${dms.audit.routing-key:audit.#}") String routingKey) {
        this.amqpTemplate = amqpTemplate;
        this.enabled = enabled;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    public void publish(AuditEventMessage event) {
        if (!enabled || event == null) {
            return;
        }
        try {
            amqpTemplate.convertAndSend(exchange, routingKey, event);
        } catch (AmqpException ex) {
            log.warn("Failed to publish audit event type={} entityId={}", event.eventType(), event.entityId(), ex);
        }
    }
}
