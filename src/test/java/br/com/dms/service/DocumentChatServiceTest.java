package br.com.dms.service;

import br.com.dms.controller.request.DocumentChatRequest;
import br.com.dms.controller.response.DocumentChatResponse;
import br.com.dms.controller.response.DocumentInsightResponse;
import br.com.dms.controller.response.DocumentRagContextResponse;
import br.com.dms.domain.mongodb.DmsDocument;
import br.com.dms.repository.mongo.DmsDocumentRepository;
import br.com.dms.repository.mongo.DmsDocumentVersionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentChatServiceTest {

    @Test
    void shouldReturnDisabledWhenFeatureFlagsAreOff() {
        TenantContextService tenantContextService = mock(TenantContextService.class);
        DmsDocumentRepository docRepo = mock(DmsDocumentRepository.class);
        DmsDocumentVersionRepository versionRepo = mock(DmsDocumentVersionRepository.class);
        DocumentInsightService insightService = mock(DocumentInsightService.class);

        DocumentChatService service = new DocumentChatService(
                tenantContextService,
                docRepo,
                versionRepo,
                insightService,
                new SimpleMeterRegistry(),
                false,
                false,
                true,
                "http://localhost:11434",
                "llama3.1:8b",
                5000,
                120000
        );

        DocumentChatRequest request = new DocumentChatRequest();
        request.setMessage("resuma o documento");

        DocumentChatResponse response = service.chat("doc-1", request);

        assertFalse(response.isEnabled());
        assertEquals("DISABLED", response.getStatus());
        assertNotNull(response.getLatencyMs());
    }

    @Test
    void shouldReturnProviderUnavailableGracefully() {
        TenantContextService tenantContextService = mock(TenantContextService.class);
        DmsDocumentRepository docRepo = mock(DmsDocumentRepository.class);
        DmsDocumentVersionRepository versionRepo = mock(DmsDocumentVersionRepository.class);
        DocumentInsightService insightService = mock(DocumentInsightService.class);

        when(tenantContextService.requireTenantId()).thenReturn("tenant-a");

        DmsDocument document = DmsDocument.of()
                .id("doc-1")
                .tenantId("tenant-a")
                .filename("contrato.pdf")
                .category("LEGAL")
                .ocrText("Parágrafo um com texto do OCR.\n\nParágrafo dois com mais detalhes.")
                .metadata(Map.of("cliente", "ACME"))
                .build();
        when(docRepo.findByIdAndTenantId("doc-1", "tenant-a")).thenReturn(Optional.of(document));
        when(insightService.getRagContextSkeleton(org.mockito.ArgumentMatchers.eq("doc-1"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(DocumentRagContextResponse.builder()
                        .documentId("doc-1")
                        .enabled(true)
                        .status("READY")
                        .message("Contexto pronto")
                        .rolloutGuard("NONE")
                        .chunks(java.util.List.of())
                        .build());
        when(insightService.getInsight(org.mockito.ArgumentMatchers.eq("doc-1"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(DocumentInsightResponse.builder()
                        .documentId("doc-1")
                        .ocrQualityScore(72)
                        .ocrQualityBand("MEDIUM")
                        .ocrQualitySummary("Qualidade OCR moderada")
                        .build());

        DocumentChatService service = new DocumentChatService(
                tenantContextService,
                docRepo,
                versionRepo,
                insightService,
                new SimpleMeterRegistry(),
                true,
                true,
                true,
                "http://localhost:65530",
                "llama3.1:8b",
                500,
                500
        );

        DocumentChatRequest request = new DocumentChatRequest();
        request.setMessage("qual é o cliente?");

        DocumentChatResponse response = service.chat("doc-1", request);

        assertTrue(response.isEnabled());
        assertEquals("PROVIDER_UNAVAILABLE", response.getStatus());
        assertFalse(response.getContextChunks().isEmpty());
        assertEquals(72, response.getOcrQualityScore());
        assertEquals("MEDIUM", response.getOcrQualityBand());
        assertNotNull(response.getLatencyMs());
    }
}
