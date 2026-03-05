package br.com.dms.service;

import br.com.dms.controller.response.MetadataSuggestionResponse;
import br.com.dms.domain.mongodb.Category;
import br.com.dms.domain.mongodb.DmsDocument;
import br.com.dms.domain.mongodb.DmsDocumentVersion;
import br.com.dms.repository.mongo.CategoryRepository;
import br.com.dms.repository.mongo.DmsDocumentRepository;
import br.com.dms.repository.mongo.DmsDocumentVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiMetadataSuggestionServiceTest {

    @Mock
    private DmsDocumentRepository dmsDocumentRepository;
    @Mock
    private DmsDocumentVersionRepository dmsDocumentVersionRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private TenantContextService tenantContextService;

    private AiMetadataSuggestionService service;

    @BeforeEach
    void setup() {
        service = new AiMetadataSuggestionService(dmsDocumentRepository, dmsDocumentVersionRepository, categoryRepository, tenantContextService);
    }

    @Test
    void shouldExtractCpfAndDateFromOcrText() {
        String tenantId = "t1";
        String documentId = "doc-1";

        DmsDocument document = DmsDocument.of()
            .id(documentId)
            .tenantId(tenantId)
            .category("CONTRATO")
            .filename("contrato.pdf")
            .ocrText("CPF: 123.456.789-01\nData Emissao: 05/03/2026")
            .build();

        DmsDocumentVersion version = DmsDocumentVersion.of()
            .dmsDocumentId(documentId)
            .versionNumber(new BigDecimal("1.0"))
            .build();

        Category category = Category.builder()
            .name("CONTRATO")
            .schema(Map.of("properties", Map.of(
                "cpf", Map.of("title", "CPF"),
                "dataEmissao", Map.of("title", "Data Emissao")
            )))
            .build();

        when(tenantContextService.requireTenantId()).thenReturn(tenantId);
        when(dmsDocumentRepository.findByIdAndTenantId(documentId, tenantId)).thenReturn(Optional.of(document));
        when(dmsDocumentVersionRepository.findLastVersionByTenantIdAndDmsDocumentId(tenantId, documentId)).thenReturn(Optional.of(version));
        when(categoryRepository.findByTenantIdAndName(tenantId, "CONTRATO")).thenReturn(Optional.of(category));

        MetadataSuggestionResponse response = service.suggest(documentId, Optional.empty());

        assertEquals("123.456.789-01", response.getSuggestedMetadata().get("cpf"));
        assertEquals("05/03/2026", response.getSuggestedMetadata().get("dataEmissao"));
        assertTrue(response.getConfidence() > 0);
    }
}
