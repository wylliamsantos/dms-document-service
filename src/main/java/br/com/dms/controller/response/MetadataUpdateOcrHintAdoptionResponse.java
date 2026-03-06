package br.com.dms.controller.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class MetadataUpdateOcrHintAdoptionResponse {
    long documentTotalUpdates;
    long documentOcrHintUpdates;
    long documentOcrHintCancelUpdates;
    long documentOcrHintErrorUpdates;
    double documentOcrHintRate;
    long categoryTotalUpdates;
    long categoryOcrHintUpdates;
    long categoryOcrHintCancelUpdates;
    long categoryOcrHintErrorUpdates;
    double categoryOcrHintRate;
    int lookbackDaysApplied;
    List<MetadataUpdateAdoptionTrendPointResponse> trend;
}
