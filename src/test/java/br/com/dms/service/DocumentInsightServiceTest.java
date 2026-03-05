package br.com.dms.service;

import br.com.dms.controller.response.DocumentRagContextResponse;
import br.com.dms.controller.response.MetadataSuggestionResponse;
import br.com.dms.domain.mongodb.DmsDocument;
import br.com.dms.repository.mongo.DmsDocumentRepository;
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

        TenantContextService tenantContextService = mock(TenantContextService.class);
        when(tenantContextService.requireTenantId()).thenReturn("tenant-1");
        DmsDocumentRepository repository = mock(DmsDocumentRepository.class);
        when(repository.findByIdAndTenantId("doc-1", "tenant-1")).thenReturn(Optional.of(
                DmsDocument.of()
                        .id("doc-1")
                        .tenantId("tenant-1")
                        .ocrText("linha 1\n\nlinha 2")
                        .metadata(Map.of("numero", "123", "valor", 42))
                        .build()
        ));

        DocumentInsightService service = new DocumentInsightService(
                aiService,
                tenantContextService,
                repository,
                false,
                ""
        );
        var response = service.getInsight("doc-1", Optional.of("2"));

        assertEquals("doc-1", response.getDocumentId());
        assertEquals("Resumo", response.getSummary());
        assertEquals("123", response.getKeyMetadata().get("cpf"));
        assertEquals(1, response.getWarnings().size());
        assertEquals("MEDIUM", response.getConfidenceBand());
        assertNotNull(response.getGeneratedAt());
        assertTrue(response.getSignals().stream().anyMatch(signal -> "ocr".equals(signal.getSignal()) && signal.isActive()));
        assertTrue(response.getSignals().stream().anyMatch(signal -> "heuristics".equals(signal.getSignal()) && signal.isActive()));
        assertEquals(4, response.getOcrStats().get("words"));
        assertEquals("123", response.getPersistedMetadataPreview().get("numero"));
    }

    @Test
    void shouldReturnDisabledRagSkeletonWhenFeatureFlagOff() {
        DocumentInsightService service = new DocumentInsightService(
                mock(AiMetadataSuggestionService.class),
                mock(TenantContextService.class),
                mock(br.com.dms.repository.mongo.DmsDocumentRepository.class),
                false,
                ""
        );

        DocumentRagContextResponse response = service.getRagContextSkeleton("doc-2", Optional.empty());
        assertFalse(response.isEnabled());
        assertEquals("DISABLED", response.getStatus());
        assertTrue(response.getChunks().isEmpty());
    }

    @Test
    void shouldReturnTenantDisabledWhenTenantNotInAllowlist() {
        TenantContextService tenantContextService = mock(TenantContextService.class);
        when(tenantContextService.requireTenantId()).thenReturn("tenant-blocked");

        DocumentInsightService service = new DocumentInsightService(
                mock(AiMetadataSuggestionService.class),
                tenantContextService,
                mock(DmsDocumentRepository.class),
                true,
                "tenant-1,tenant-2"
        );

        DocumentRagContextResponse response = service.getRagContextSkeleton("doc-2", Optional.empty());
        assertFalse(response.isEnabled());
        assertEquals("TENANT_DISABLED", response.getStatus());
        assertTrue(response.getChunks().isEmpty());
    }

    @Test
    void shouldExposeRankedRagChunksWithScoreAndSource() {
        TenantContextService tenantContextService = mock(TenantContextService.class);
        when(tenantContextService.requireTenantId()).thenReturn("tenant-1");

        DmsDocumentRepository repository = mock(DmsDocumentRepository.class);
        when(repository.findByIdAndTenantId("doc-rag", "tenant-1")).thenReturn(Optional.of(
                DmsDocument.of()
                        .id("doc-rag")
                        .tenantId("tenant-1")
                        .ocrText("Primeiro parágrafo com conteúdo relevante.\n\nSegundo parágrafo com mais contexto.")
                        .build()
        ));

        DocumentInsightService service = new DocumentInsightService(
                mock(AiMetadataSuggestionService.class),
                tenantContextService,
                repository,
                true,
                "tenant-1"
        );

        DocumentRagContextResponse response = service.getRagContextSkeleton("doc-rag", Optional.empty());
        assertTrue(response.isEnabled());
        assertEquals("READY", response.getStatus());
        assertFalse(response.getChunks().isEmpty());
        assertEquals("ocr", response.getChunks().get(0).getSource());
        assertTrue(response.getChunks().get(0).getScore() > 0.0d);
        assertNotNull(response.getChunks().get(0).getExcerpt());
    }
}
