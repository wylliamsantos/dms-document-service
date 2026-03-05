package br.com.dms.service;

import br.com.dms.domain.mongodb.Category;
import br.com.dms.domain.mongodb.DmsDocument;
import br.com.dms.repository.mongo.CategoryRepository;
import br.com.dms.repository.mongo.DmsDocumentRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentRetentionJobServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private DmsDocumentRepository dmsDocumentRepository;

    private DocumentRetentionJobService service;

    @BeforeEach
    void setUp() {
        service = new DocumentRetentionJobService(categoryRepository, dmsDocumentRepository);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "batchSize", 100);
    }

    @Test
    void shouldArchiveAndDeleteAccordingToPolicy() {
        Category category = Category.builder()
            .tenantId("tenant-a")
            .name("CONTRATO")
            .archiveAfterDays(30L)
            .retentionDays(60L)
            .build();

        DmsDocument archiveCandidate = documentWithAgeInDays(35);
        DmsDocument deleteCandidate = documentWithAgeInDays(75);

        when(categoryRepository.findAll()).thenReturn(List.of(category));
        when(dmsDocumentRepository.findByTenantIdAndCategory("tenant-a", "CONTRATO"))
            .thenReturn(List.of(archiveCandidate, deleteCandidate));

        service.execute();

        ArgumentCaptor<List<DmsDocument>> saveCaptor = ArgumentCaptor.forClass(List.class);
        verify(dmsDocumentRepository).saveAll(saveCaptor.capture());
        DmsDocument archivedDocument = saveCaptor.getValue().getFirst();
        assertThat(archivedDocument.getArchived()).isTrue();
        assertThat(archivedDocument.getArchivedAt()).isNotNull();

        ArgumentCaptor<List<DmsDocument>> deleteCaptor = ArgumentCaptor.forClass(List.class);
        verify(dmsDocumentRepository).deleteAll(deleteCaptor.capture());
        DmsDocument deletedDocument = deleteCaptor.getValue().getFirst();
        assertThat(deletedDocument.getExpiredAt()).isNotNull();
    }

    @Test
    void shouldSkipWhenRetentionDisabled() {
        ReflectionTestUtils.setField(service, "enabled", false);

        service.execute();

        verifyNoInteractions(categoryRepository);
        verify(dmsDocumentRepository, never()).saveAll(anyList());
        verify(dmsDocumentRepository, never()).deleteAll(anyList());
    }

    @Test
    void shouldApplyInclusiveThresholdsAtBoundaryDays() {
        Category category = Category.builder()
            .tenantId("tenant-a")
            .name("CONTRATO")
            .archiveAfterDays(30L)
            .retentionDays(60L)
            .build();

        DmsDocument archiveBoundary = documentWithAgeInDays(30);
        DmsDocument deleteBoundary = documentWithAgeInDays(60);

        when(categoryRepository.findAll()).thenReturn(List.of(category));
        when(dmsDocumentRepository.findByTenantIdAndCategory("tenant-a", "CONTRATO"))
            .thenReturn(List.of(archiveBoundary, deleteBoundary));

        service.execute();

        ArgumentCaptor<List<DmsDocument>> saveCaptor = ArgumentCaptor.forClass(List.class);
        verify(dmsDocumentRepository).saveAll(saveCaptor.capture());
        assertThat(saveCaptor.getValue()).hasSize(1);
        assertThat(saveCaptor.getValue().get(0).getId()).isEqualTo(archiveBoundary.getId());

        ArgumentCaptor<List<DmsDocument>> deleteCaptor = ArgumentCaptor.forClass(List.class);
        verify(dmsDocumentRepository).deleteAll(deleteCaptor.capture());
        assertThat(deleteCaptor.getValue()).hasSize(1);
        assertThat(deleteCaptor.getValue().get(0).getId()).isEqualTo(deleteBoundary.getId());
    }

    @Test
    void shouldNotArchiveAlreadyArchivedDocumentAgain() {
        Category category = Category.builder()
            .tenantId("tenant-a")
            .name("CONTRATO")
            .archiveAfterDays(30L)
            .build();

        DmsDocument alreadyArchived = documentWithAgeInDays(45);
        alreadyArchived.setArchived(Boolean.TRUE);
        alreadyArchived.setArchivedAt(Instant.now().minus(5, ChronoUnit.DAYS));

        when(categoryRepository.findAll()).thenReturn(List.of(category));
        when(dmsDocumentRepository.findByTenantIdAndCategory("tenant-a", "CONTRATO"))
            .thenReturn(List.of(alreadyArchived));

        service.execute();

        verify(dmsDocumentRepository, never()).saveAll(anyList());
        verify(dmsDocumentRepository, never()).deleteAll(anyList());
    }

    private DmsDocument documentWithAgeInDays(long days) {
        Instant createdAt = Instant.now().minus(days, ChronoUnit.DAYS);
        return DmsDocument.of()
            .id(new ObjectId(java.util.Date.from(createdAt)).toHexString())
            .category("CONTRATO")
            .tenantId("tenant-a")
            .build();
    }
}
