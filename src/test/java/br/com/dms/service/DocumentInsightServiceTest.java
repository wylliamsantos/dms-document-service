package br.com.dms.service;

import br.com.dms.controller.response.DocumentRagContextResponse;
import br.com.dms.controller.response.MetadataSuggestionResponse;
import br.com.dms.domain.mongodb.Category;
import br.com.dms.domain.mongodb.DmsDocument;
import br.com.dms.domain.mongodb.MetadataUpdateHistoryEntry;
import br.com.dms.repository.mongo.CategoryRepository;
import br.com.dms.repository.mongo.DmsDocumentRepository;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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
                        .metadata(Map.of("numero", "123", "valor", 42, "observacao", "ignorar"))
                        .metadataUpdateHistory(List.of(
                                MetadataUpdateHistoryEntry.builder()
                                        .field("valor")
                                        .previousValue("41")
                                        .newValue("42")
                                        .source("OCR_HINT")
                                        .updatedAt("2026-03-06T08:20:00Z")
                                        .updatedBy("tester")
                                        .build()
                        ))
                        .build()
        ));

        DocumentInsightService service = new DocumentInsightService(
                aiService,
                tenantContextService,
                repository,
                mock(br.com.dms.repository.mongo.CategoryRepository.class),
                new SimpleMeterRegistry(),
                false,
                "",
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
        assertEquals(42, response.getImportantPersistedMetadata().get("valor"));
        assertEquals(3, response.getPersistedMetadataCount());
        assertTrue(response.getHasPersistedOcrText());
        assertEquals(100, response.getRequiredMetadataCoveragePercent());
        assertTrue(response.getMetadataActionHints().isEmpty());
        assertEquals(1, response.getMetadataUpdateHistory().size());
        assertEquals("OCR_HINT", response.getMetadataUpdateHistory().get(0).getSource());
        assertFalse(response.getImportantPersistedMetadata().containsKey("observacao"));
    }

    @Test
    void shouldFilterMetadataHistoryBySourceFieldAndPeriod() {
        AiMetadataSuggestionService aiService = mock(AiMetadataSuggestionService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        when(tenantContextService.requireTenantId()).thenReturn("tenant-1");

        DmsDocumentRepository repository = mock(DmsDocumentRepository.class);
        when(repository.findByIdAndTenantId("doc-history", "tenant-1")).thenReturn(Optional.of(
                DmsDocument.of()
                        .id("doc-history")
                        .tenantId("tenant-1")
                        .metadataUpdateHistory(List.of(
                                MetadataUpdateHistoryEntry.builder()
                                        .field("valor")
                                        .previousValue("100")
                                        .newValue("120")
                                        .source("OCR_HINT")
                                        .updatedAt("2026-03-06T08:00:00Z")
                                        .updatedBy("ocr-bot")
                                        .build(),
                                MetadataUpdateHistoryEntry.builder()
                                        .field("cpf")
                                        .previousValue("111")
                                        .newValue("222")
                                        .source("MANUAL")
                                        .updatedAt("2026-03-06T07:00:00Z")
                                        .updatedBy("user-a")
                                        .build()
                        ))
                        .build()
        ));

        DocumentInsightService service = new DocumentInsightService(
                aiService,
                tenantContextService,
                repository,
                mock(CategoryRepository.class),
                new SimpleMeterRegistry(),
                false,
                "",
                ""
        );

        var page = service.getMetadataUpdateHistory(
                "doc-history",
                Optional.empty(),
                0,
                10,
                Optional.of("OCR_HINT"),
                Optional.of("valor"),
                Optional.of(Instant.parse("2026-03-06T07:59:00Z")),
                Optional.of(Instant.parse("2026-03-06T08:01:00Z"))
        );

        assertEquals(1, page.getTotalElements());
        assertEquals("valor", page.getContent().get(0).getField());
        assertEquals("OCR_HINT", page.getContent().get(0).getSource());
    }

    @Test
    void shouldBuildMetadataHistorySummaryWithTopBuckets() {
        AiMetadataSuggestionService aiService = mock(AiMetadataSuggestionService.class);
        TenantContextService tenantContextService = mock(TenantContextService.class);
        when(tenantContextService.requireTenantId()).thenReturn("tenant-1");

        DmsDocumentRepository repository = mock(DmsDocumentRepository.class);
        when(repository.findByIdAndTenantId("doc-history", "tenant-1")).thenReturn(Optional.of(
                DmsDocument.of()
                        .id("doc-history")
                        .tenantId("tenant-1")
                        .metadataUpdateHistory(List.of(
                                MetadataUpdateHistoryEntry.builder().field("valor").source("OCR_HINT").updatedAt("2026-03-06T09:00:00Z").build(),
                                MetadataUpdateHistoryEntry.builder().field("valor").source("OCR_HINT").updatedAt("2026-03-06T08:30:00Z").build(),
                                MetadataUpdateHistoryEntry.builder().field("cpf").source("MANUAL").updatedAt("2026-03-06T08:00:00Z").build()
                        ))
                        .build()
        ));

        DocumentInsightService service = new DocumentInsightService(
                aiService,
                tenantContextService,
                repository,
                mock(CategoryRepository.class),
                new SimpleMeterRegistry(),
                false,
                "",
                ""
        );

        var summary = service.getMetadataUpdateHistorySummary(
                "doc-history",
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );

        assertEquals(3, summary.getTotalEntries());
        assertEquals(3, summary.getFilteredEntries());
        assertEquals("2026-03-06T09:00:00Z", summary.getLatestUpdatedAt());
        assertEquals("ocr_hint", summary.getBySource().get(0).getKey());
        assertEquals(2L, summary.getBySource().get(0).getCount());
        assertEquals("valor", summary.getByField().get(0).getKey());
    }

    @Test
    void shouldExposeMissingRequiredMetadataFromCategorySchema() {
        AiMetadataSuggestionService aiService = mock(AiMetadataSuggestionService.class);
        when(aiService.suggest(eq("doc-2"), eq(Optional.empty()))).thenReturn(
                MetadataSuggestionResponse.builder()
                        .documentId("doc-2")
                        .summary("Resumo")
                        .confidence(0.9)
                        .source("metadata")
                        .build()
        );

        TenantContextService tenantContextService = mock(TenantContextService.class);
        when(tenantContextService.requireTenantId()).thenReturn("tenant-1");

        DmsDocumentRepository repository = mock(DmsDocumentRepository.class);
        when(repository.findByIdAndTenantId("doc-2", "tenant-1")).thenReturn(Optional.of(
                DmsDocument.of()
                        .id("doc-2")
                        .tenantId("tenant-1")
                        .category("CONTRATO")
                        .metadata(Map.of("cpf", "123"))
                        .build()
        ));

        CategoryRepository categoryRepository = mock(CategoryRepository.class);
        when(categoryRepository.findByTenantIdAndName("tenant-1", "CONTRATO")).thenReturn(Optional.of(
                Category.builder()
                        .name("CONTRATO")
                        .schema(Map.of("required", List.of("cpf", "valor", "data_emissao")))
                        .build()
        ));

        DocumentInsightService service = new DocumentInsightService(
                aiService,
                tenantContextService,
                repository,
                categoryRepository,
                new SimpleMeterRegistry(),
                false,
                "",
                ""
        );

        var response = service.getInsight("doc-2", Optional.empty());
        assertEquals(List.of("cpf", "valor", "data_emissao"), response.getExpectedRequiredMetadata());
        assertEquals(List.of("valor", "data_emissao"), response.getMissingRequiredMetadata());
        assertEquals(33, response.getRequiredMetadataCoveragePercent());
        assertEquals(2, response.getMetadataActionHints().size());
        assertEquals("valor", response.getMetadataActionHints().get(0).getField());
        assertEquals("REQUEST_OCR_PROCESSING", response.getMetadataActionHints().get(0).getAction());
    }

    @Test
    void shouldReturnDisabledRagSkeletonWhenFeatureFlagOff() {
        DocumentInsightService service = new DocumentInsightService(
                mock(AiMetadataSuggestionService.class),
                mock(TenantContextService.class),
                mock(br.com.dms.repository.mongo.DmsDocumentRepository.class),
                mock(br.com.dms.repository.mongo.CategoryRepository.class),
                new SimpleMeterRegistry(),
                false,
                "",
                ""
        );

        DocumentRagContextResponse response = service.getRagContextSkeleton("doc-2", Optional.empty());
        assertFalse(response.isEnabled());
        assertEquals("DISABLED", response.getStatus());
        assertEquals(0, response.getChunkCount());
        assertEquals("unknown", response.getCategory());
        assertTrue(response.getAverageScore() >= 0.0d);
        assertTrue(response.getLatencyMs() >= 0L);
        assertEquals("BLOCKED", response.getQualityBand());
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
                mock(br.com.dms.repository.mongo.CategoryRepository.class),
                new SimpleMeterRegistry(),
                true,
                "tenant-1,tenant-2",
                ""
        );

        DocumentRagContextResponse response = service.getRagContextSkeleton("doc-2", Optional.empty());
        assertFalse(response.isEnabled());
        assertEquals("TENANT_DISABLED", response.getStatus());
        assertEquals("unknown", response.getCategory());
        assertEquals("BLOCKED", response.getQualityBand());
        assertTrue(response.getChunks().isEmpty());
    }



    @Test
    void shouldReturnCategoryDisabledWhenCategoryNotInAllowlist() {
        TenantContextService tenantContextService = mock(TenantContextService.class);
        when(tenantContextService.requireTenantId()).thenReturn("tenant-1");

        DmsDocumentRepository repository = mock(DmsDocumentRepository.class);
        when(repository.findByIdAndTenantId("doc-rag", "tenant-1")).thenReturn(Optional.of(
                DmsDocument.of()
                        .id("doc-rag")
                        .tenantId("tenant-1")
                        .category("CONTRATO")
                        .ocrText("Texto")
                        .build()
        ));

        DocumentInsightService service = new DocumentInsightService(
                mock(AiMetadataSuggestionService.class),
                tenantContextService,
                repository,
                mock(br.com.dms.repository.mongo.CategoryRepository.class),
                new SimpleMeterRegistry(),
                true,
                "tenant-1",
                "nota_fiscal"
        );

        DocumentRagContextResponse response = service.getRagContextSkeleton("doc-rag", Optional.empty());
        assertFalse(response.isEnabled());
        assertEquals("CATEGORY_DISABLED", response.getStatus());
        assertEquals("CONTRATO", response.getCategory());
        assertEquals("BLOCKED", response.getQualityBand());
        assertTrue(response.getChunks().isEmpty());
    }

    @Test
    void shouldReturnQualityGatedWhenRequiredMetadataIsMissing() {
        TenantContextService tenantContextService = mock(TenantContextService.class);
        when(tenantContextService.requireTenantId()).thenReturn("tenant-1");

        DmsDocumentRepository repository = mock(DmsDocumentRepository.class);
        when(repository.findByIdAndTenantId("doc-rag", "tenant-1")).thenReturn(Optional.of(
                DmsDocument.of()
                        .id("doc-rag")
                        .tenantId("tenant-1")
                        .category("CONTRATO")
                        .metadata(Map.of("cpf", "123"))
                        .ocrText("Parágrafo OCR válido\nvalor: 900,00")
                        .build()
        ));

        CategoryRepository categoryRepository = mock(CategoryRepository.class);
        when(categoryRepository.findByTenantIdAndName("tenant-1", "CONTRATO")).thenReturn(Optional.of(
                Category.builder()
                        .name("CONTRATO")
                        .schema(Map.of("required", List.of("cpf", "valor")))
                        .build()
        ));

        AiMetadataSuggestionService aiService = mock(AiMetadataSuggestionService.class);
        when(aiService.suggest(eq("doc-rag"), eq(Optional.empty()))).thenReturn(
                MetadataSuggestionResponse.builder()
                        .documentId("doc-rag")
                        .summary("Resumo")
                        .confidence(0.7)
                        .source("ocr")
                        .build()
        );

        DocumentInsightService service = new DocumentInsightService(
                aiService,
                tenantContextService,
                repository,
                categoryRepository,
                new SimpleMeterRegistry(),
                true,
                "tenant-1",
                "contrato"
        );

        DocumentRagContextResponse response = service.getRagContextSkeleton("doc-rag", Optional.empty());
        assertFalse(response.isEnabled());
        assertEquals("QUALITY_GATED", response.getStatus());
        assertEquals("BLOCKED", response.getQualityBand());
        assertEquals(List.of("valor"), response.getMissingRequiredMetadata());
        var insight = service.getInsight("doc-rag", Optional.empty());
        assertEquals("EXTRACT_FROM_OCR", insight.getMetadataActionHints().get(0).getAction());
        assertEquals("900,00", insight.getMetadataActionHints().get(0).getSuggestedValue());
        assertNotNull(insight.getMetadataActionHints().get(0).getEvidenceExcerpt());
        assertTrue(response.getMessage().contains("metadados obrigatórios"));
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

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        DocumentInsightService service = new DocumentInsightService(
                mock(AiMetadataSuggestionService.class),
                tenantContextService,
                repository,
                mock(br.com.dms.repository.mongo.CategoryRepository.class),
                meterRegistry,
                true,
                "tenant-1",
                ""
        );

        DocumentRagContextResponse response = service.getRagContextSkeleton("doc-rag", Optional.empty());
        assertTrue(response.isEnabled());
        assertEquals("READY", response.getStatus());
        assertEquals("unknown", response.getCategory());
        assertFalse(response.getChunks().isEmpty());
        assertEquals(response.getChunks().size(), response.getChunkCount());
        assertTrue(response.getAverageScore() > 0.0d);
        assertTrue(response.getLatencyMs() >= 0L);
        assertNotNull(response.getQualityBand());
        assertEquals("ocr", response.getChunks().get(0).getSource());
        assertTrue(response.getChunks().get(0).getScore() > 0.0d);
        assertNotNull(response.getChunks().get(0).getExcerpt());

        Timer timer = meterRegistry.find("dms.ai.document.rag.latency").timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
    }
}
