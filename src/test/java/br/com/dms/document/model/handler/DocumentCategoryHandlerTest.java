package br.com.dms.service.handler;

import br.com.dms.domain.mongodb.Category;
import br.com.dms.domain.mongodb.CategoryType;
import br.com.dms.domain.core.DocumentCategory;
import br.com.dms.domain.core.DocumentGroup;
import br.com.dms.repository.mongo.CategoryRepository;
import br.com.dms.exception.DmsBusinessException;
import br.com.dms.service.TenantContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentCategoryHandlerTest {

    private static final String TRANSACTION_ID = "tx-123";

    @Mock
    private CategoryRepository categoryRepository;


    @Mock
    private Environment environment;

    @Mock
    private TenantContextService tenantContextService;

    @InjectMocks
    private DocumentCategoryHandler handler;

    @BeforeEach
    void setUp() {
        when(environment.getProperty("dms.msg.typeInvalid")).thenReturn("Tipo inválido");
        when(tenantContextService.requireTenantId()).thenReturn("tenant-a");
    }

    @Test
    void loadCategoryShouldMapMongoModel() {
        Category category = Category.builder()
                .name("category::test")
                .description("Test description")
                .uniqueAttributes("cpf")
                .validityInDays(365L)
                .documentGroup(DocumentGroup.PERSONAL)
                .build();

        when(categoryRepository.findByTenantIdAndName("tenant-a", "category::test")).thenReturn(Optional.of(category));

        DocumentCategory documentCategory = handler.loadCategory(TRANSACTION_ID, "category::test");

        assertThat(documentCategory.getName()).isEqualTo("category::test");
        assertThat(documentCategory.getDescription()).isEqualTo("Test description");
        assertThat(documentCategory.getUniqueAttributes()).isEqualTo("cpf");
        assertThat(documentCategory.getValidityInDays()).isEqualTo(365L);
    }

    @Test
    void loadCategoryShouldThrowWhenUnknown() {
        when(categoryRepository.findByTenantIdAndName("tenant-a", "unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.loadCategory(TRANSACTION_ID, "unknown"))
                .isInstanceOf(DmsBusinessException.class)
                .hasMessageContaining("Tipo inválido");
    }

    @Test
    void loadCategoryWithTypeShouldReturnDocumentType() {
        CategoryType drivingLicense = CategoryType.builder()
                .name("CNH")
                .description("Carteira")
                .validityInDays(180L)
                .requiredAttributes("cpf")
                .build();

        Category category = Category.builder()
                .name("category::test")
                .types(List.of(drivingLicense))
                .build();

        when(categoryRepository.findByTenantIdAndName("tenant-a", "category::test")).thenReturn(Optional.of(category));

        DocumentCategory documentCategory = handler.loadCategoryWithType(TRANSACTION_ID, "category::test", "CNH");

        assertThat(documentCategory.getDocumentType()).isNotNull();
        assertThat(documentCategory.getDocumentType().getName()).isEqualTo("CNH");
        assertThat(documentCategory.getDocumentType().getRequiredAttributes()).isEqualTo("cpf");
    }

    @Test
    void loadCategoryWithTypeShouldFailWhenTypeMissing() {
        Category category = Category.builder()
                .name("category::test")
                .types(List.of())
                .build();

        when(categoryRepository.findByTenantIdAndName("tenant-a", "category::test")).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> handler.loadCategoryWithType(TRANSACTION_ID, "category::test", "CNH"))
                .isInstanceOf(DmsBusinessException.class)
                .hasMessageContaining("Tipo inválido");
    }

    @Test
    void resolveCategoryShouldLoadTypeFromMetadata() {
        CategoryType drivingLicense = CategoryType.builder()
                .name("CNH")
                .description("Carteira")
                .validityInDays(365L)
                .requiredAttributes("cpf")
                .build();

        Category category = Category.builder()
                .name("category::test")
                .types(List.of(drivingLicense))
                .build();

        when(categoryRepository.findByTenantIdAndName("tenant-a", "category::test")).thenReturn(Optional.of(category));

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("tipoDocumento", "CNH");

        DocumentCategory documentCategory = handler.resolveCategory(TRANSACTION_ID, "category::test", metadata);

        assertThat(documentCategory.getDocumentType()).isNotNull();
        assertThat(documentCategory.getDocumentType().getName()).isEqualTo("CNH");
    }

    @Test
    void resolveCategoryShouldReturnWithoutTypeWhenMetadataMissing() {
        Category category = Category.builder()
                .name("category::test")
                .build();

        when(categoryRepository.findByTenantIdAndName("tenant-a", "category::test")).thenReturn(Optional.of(category));

        DocumentCategory documentCategory = handler.resolveCategory(TRANSACTION_ID, "category::test", Map.of());

        assertThat(documentCategory.getDocumentType()).isNull();
    }
}
