package br.com.dms.audit;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditEventMessage(
    String eventType,
    Instant occurredAt,
    String userId,
    String tenantId,
    String entityType,
    String entityId,
    String filename,
    Map<String, Object> metadata,
    Map<String, Object> attributes
) {
}
