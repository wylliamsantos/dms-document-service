package br.com.dms.controller.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
@Builder
public class MetadataSuggestionResponse {
    String documentId;
    String version;
    String category;
    String suggestedCategory;
    Map<String, Object> suggestedMetadata;
    String summary;
    List<String> consistencyWarnings;
    double confidence;
    String source;
}
