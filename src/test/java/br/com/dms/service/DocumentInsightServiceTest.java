package br.com.dms.service;

import br.com.dms.controller.response.DocumentRagContextResponse;
import br.com.dms.controller.response.MetadataSuggestionResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentInsightServiceTest {

    @Test
    void shouldBuildInsightFromMetadataSuggestionService() {
        AiMetadataSuggestionService aiService = mock(AiMetadataSuggestionService.class);
        when(aiService.suggest(eq("doc-1"), eq(Optional.of("2")))).thenReturn(
                MetadataSuggestionResponse.builder()
                        .documentId("doc-1")
                        .version("2")
                        .summary("Resumo")
                        .suggestedMetadata(Map.of("cpf", "123"))
                        .consistencyWarnings(List.of("warning"))
                        .confidence(0.8)
                        .source("ocr+heuristics")
                        .build()
        );

        DocumentInsightService service = new DocumentInsightService(
                aiService,
                mock(TenantContextService.class),
                mock(br.com.dms.repository.mongo.DmsDocumentRepository.class),
                false
        );
        var response = service.getInsight("doc-1", Optional.of("2"));

        assertEquals("doc-1", response.getDocumentId());
        assertEquals("Resumo", response.getSummary());
        assertEquals("123", response.getKeyMetadata().get("cpf"));
        assertEquals(1, response.getWarnings().size());
        assertEquals("MEDIUM", response.getConfidenceBand());
        assertTrue(response.getSignals().stream().anyMatch(signal -> "ocr".equals(signal.getSignal()) && signal.isActive()));
        assertTrue(response.getSignals().stream().anyMatch(signal -> "heuristics".equals(signal.getSignal()) && signal.isActive()));
    }

    @Test
    void shouldReturnDisabledRagSkeletonWhenFeatureFlagOff() {
        DocumentInsightService service = new DocumentInsightService(
                mock(AiMetadataSuggestionService.class),
                mock(TenantContextService.class),
                mock(br.com.dms.repository.mongo.DmsDocumentRepository.class),
                false
        );

        DocumentRagContextResponse response = service.getRagContextSkeleton("doc-2", Optional.empty());
        assertFalse(response.isEnabled());
        assertEquals("DISABLED", response.getStatus());
        assertTrue(response.getChunks().isEmpty());
    }
}
