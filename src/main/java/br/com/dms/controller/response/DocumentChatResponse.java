package br.com.dms.controller.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class DocumentChatResponse {
    String documentId;
    String version;
    boolean enabled;
    String status;
    String message;
    String answer;
    String model;
    List<String> contextChunks;
}
