package br.com.dms.controller.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class InsightSignalResponse {
    String signal;
    String description;
    boolean active;
}
