package br.com.dms.controller.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MetadataUpdateAdoptionTrendPointResponse {
    String label;
    long totalUpdates;
    long ocrHintUpdates;
    double ocrHintRate;
}
