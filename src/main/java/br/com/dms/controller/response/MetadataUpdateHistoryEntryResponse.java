package br.com.dms.controller.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MetadataUpdateHistoryEntryResponse {
    String field;
    String previousValue;
    String newValue;
    String source;
    String updatedAt;
    String updatedBy;
}
