package br.com.dms.controller.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MetadataActionHintResponse {
    String field;
    String action;
    String reason;
    String priority;
    String suggestedValue;
    String evidenceExcerpt;
    Integer impactScore;
}
