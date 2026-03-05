package br.com.dms.controller.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RagContextChunkResponse {
    String source;
    double score;
    String excerpt;
}
