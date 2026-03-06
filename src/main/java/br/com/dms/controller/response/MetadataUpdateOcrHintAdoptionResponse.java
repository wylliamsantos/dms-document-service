package br.com.dms.controller.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class MetadataUpdateOcrHintAdoptionResponse {
    long documentTotalUpdates;
    long documentOcrHintUpdates;
    double documentOcrHintRate;
    long categoryTotalUpdates;
    long categoryOcrHintUpdates;
    double categoryOcrHintRate;
    int lookbackDaysApplied;
    List<MetadataUpdateAdoptionTrendPointResponse> trend;
}
