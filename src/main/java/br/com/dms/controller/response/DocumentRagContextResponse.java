package br.com.dms.controller.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class DocumentRagContextResponse {
    String documentId;
    String version;
    boolean enabled;
    String status;
    String message;
    String category;
    Integer chunkCount;
    Double averageScore;
    Long latencyMs;
    String qualityBand;
    String rolloutGuard;
    Boolean ragReadyForChat;
    Boolean featureFlagEnabled;
    Boolean tenantAllowed;
    Boolean categoryAllowed;
    Boolean qualityGatePassed;
    List<String> missingRequiredMetadata;
    List<RagContextChunkResponse> chunks;
}
