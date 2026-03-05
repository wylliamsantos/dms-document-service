package br.com.dms.service;

import br.com.dms.controller.response.DocumentInsightResponse;
import br.com.dms.controller.response.DocumentRagContextResponse;
import br.com.dms.controller.response.InsightSignalResponse;
import br.com.dms.controller.response.MetadataSuggestionResponse;
import br.com.dms.domain.mongodb.DmsDocument;
import br.com.dms.exception.DmsDocumentNotFoundException;
import br.com.dms.exception.TypeException;
import br.com.dms.repository.mongo.DmsDocumentRepository;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class DocumentInsightService {

    private final AiMetadataSuggestionService aiMetadataSuggestionService;
    private final TenantContextService tenantContextService;
    private final DmsDocumentRepository dmsDocumentRepository;
    private final boolean ragEnabled;

    public DocumentInsightService(AiMetadataSuggestionService aiMetadataSuggestionService,
                                  TenantContextService tenantContextService,
                                  DmsDocumentRepository dmsDocumentRepository,
                                  @Value("${dms.ai.rag.document.enabled:false}") boolean ragEnabled) {
        this.aiMetadataSuggestionService = aiMetadataSuggestionService;
        this.tenantContextService = tenantContextService;
        this.dmsDocumentRepository = dmsDocumentRepository;
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
                .confidenceBand(resolveConfidenceBand(suggestion.getConfidence()))
                .source(suggestion.getSource())
                .generatedAt(Instant.now().toString())
                .signals(resolveSignals(suggestion))
                .build();
    }

    private String resolveConfidenceBand(double confidence) {
        if (confidence >= 0.85d) {
            return "HIGH";
        }
        if (confidence >= 0.60d) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private List<InsightSignalResponse> resolveSignals(MetadataSuggestionResponse suggestion) {
        String source = StringUtils.lowerCase(StringUtils.defaultString(suggestion.getSource()));
        return List.of(
                InsightSignalResponse.builder().signal("ocr").description("Trechos OCR usados na inferência").active(source.contains("ocr")).build(),
                InsightSignalResponse.builder().signal("metadata").description("Metadados estruturados usados").active(source.contains("metadata")).build(),
                InsightSignalResponse.builder().signal("heuristics").description("Regras heurísticas aplicadas").active(source.contains("heuristic")).build(),
                InsightSignalResponse.builder().signal("filename").description("Nome do arquivo usado como sinal").active(source.contains("filename") || source.contains("name")).build()
        );
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

        String tenantId = tenantContextService.requireTenantId();
        DmsDocument document = dmsDocumentRepository.findByIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> new DmsDocumentNotFoundException("Document not found", TypeException.VALID));

        List<String> chunks = buildChunks(document);
        return DocumentRagContextResponse.builder()
                .documentId(documentId)
                .version(version.orElse(null))
                .enabled(true)
                .status("READY")
                .message(chunks.isEmpty() ? "Sem chunks de OCR disponíveis para este documento." : "Contexto RAG local carregado.")
                .chunks(chunks)
                .build();
    }

    private List<String> buildChunks(DmsDocument document) {
        String ocrText = StringUtils.trimToEmpty(document.getOcrText());
        if (StringUtils.isBlank(ocrText)) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        String[] paragraphs = ocrText.replace("\r", "\n").split("\\n\\s*\\n");
        for (String paragraph : paragraphs) {
            String clean = StringUtils.normalizeSpace(paragraph);
            if (StringUtils.isBlank(clean)) {
                continue;
            }
            chunks.add(clean.substring(0, Math.min(clean.length(), 400)));
            if (chunks.size() >= 5) {
                break;
            }
        }
        return chunks;
    }
}
