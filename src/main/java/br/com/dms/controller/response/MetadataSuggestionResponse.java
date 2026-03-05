package br.com.dms.controller.response;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@Builder
public class MetadataSuggestionResponse {
    String documentId;
    String version;
    String category;
    Map<String, Object> suggestedMetadata;
    double confidence;
    String source;
}
