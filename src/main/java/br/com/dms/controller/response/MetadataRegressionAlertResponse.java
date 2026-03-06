package br.com.dms.controller.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MetadataRegressionAlertResponse {
    String dimension;
    String key;
    long documentCount;
    long categoryCount;
    double documentRatio;
    double categoryRatio;
    double deltaRatio;
    String severity;
    String message;
}
