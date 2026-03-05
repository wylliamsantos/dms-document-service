package br.com.dms.service;

import br.com.dms.controller.response.DocumentInsightResponse;
import br.com.dms.controller.response.DocumentRagContextResponse;
import br.com.dms.controller.response.MetadataSuggestionResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DocumentInsightService {

    private final AiMetadataSuggestionService aiMetadataSuggestionService;
    private final boolean ragEnabled;

    public DocumentInsightService(AiMetadataSuggestionService aiMetadataSuggestionService,
                                  @Value("${dms.ai.rag.document.enabled:false}") boolean ragEnabled) {
        this.aiMetadataSuggestionService = aiMetadataSuggestionService;
        this.ragEnabled = ragEnabled;
    }

    public DocumentInsightResponse getInsight(String documentId, Optional<String> version) {
        MetadataSuggestionResponse suggestion = aiMetadataSuggestionService.suggest(documentId, version);
        return DocumentInsightResponse.builder()
                .documentId(suggestion.getDocumentId())
                .version(suggestion.getVersion())
                .summary(suggestion.getSummary())
                .keyMetadata(suggestion.getSuggestedMetadata())
                .warnings(suggestion.getConsistencyWarnings())
                .confidence(suggestion.getConfidence())
                .source(suggestion.getSource())
                .build();
    }

    public DocumentRagContextResponse getRagContextSkeleton(String documentId, Optional<String> version) {
        if (!ragEnabled) {
            return DocumentRagContextResponse.builder()
                    .documentId(documentId)
                    .version(version.orElse(null))
                    .enabled(false)
                    .status("DISABLED")
                    .message("RAG de documento desabilitado por feature flag (dms.ai.rag.document.enabled=false).")
                    .chunks(List.of())
                    .build();
        }

        return DocumentRagContextResponse.builder()
                .documentId(documentId)
                .version(version.orElse(null))
                .enabled(true)
                .status("SKELETON")
                .message("MVP de RAG em modo skeleton: endpoint pronto, chunking/indexação ainda não implementados.")
                .chunks(List.of())
                .build();
    }
}
