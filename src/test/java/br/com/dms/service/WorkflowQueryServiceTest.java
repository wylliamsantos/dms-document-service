package br.com.dms.service;

import br.com.dms.controller.response.WorkflowDashboardResponse;
import br.com.dms.domain.core.DocumentWorkflowStatus;
import br.com.dms.domain.mongodb.DmsDocument;
import br.com.dms.domain.mongodb.DocumentWorkflowTransition;
import br.com.dms.repository.mongo.DmsDocumentRepository;
import br.com.dms.repository.mongo.DmsDocumentVersionRepository;
import br.com.dms.repository.mongo.DocumentWorkflowTransitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowQueryServiceTest {

    @Mock
    private DmsDocumentRepository dmsDocumentRepository;

    @Mock
    private DmsDocumentVersionRepository dmsDocumentVersionRepository;

    @Mock
    private DocumentWorkflowTransitionRepository workflowTransitionRepository;

    @Mock
    private TenantContextService tenantContextService;

    private WorkflowQueryService workflowQueryService;

    @BeforeEach
    void setup() {
        workflowQueryService = new WorkflowQueryService(
            dmsDocumentRepository,
            dmsDocumentVersionRepository,
            workflowTransitionRepository,
            tenantContextService
        );
    }

    @Test
    void shouldReturnSlaAndAverageProcessingMetrics() {
        when(tenantContextService.requireTenantId()).thenReturn("tenant-a");

        DmsDocument pending = DmsDocument.of().id("doc-pending").workflowStatus(DocumentWorkflowStatus.PENDING_REVIEW).build();
        DmsDocument approved = DmsDocument.of().id("doc-approved").workflowStatus(DocumentWorkflowStatus.APPROVED).build();

        when(dmsDocumentRepository.findByTenantId("tenant-a")).thenReturn(List.of(pending, approved));

        LocalDateTime now = LocalDateTime.now();
        DocumentWorkflowTransition pendingTransition = DocumentWorkflowTransition.of()
            .documentId("doc-pending")
            .toStatus(DocumentWorkflowStatus.PENDING_REVIEW)
            .changedAt(now.minusHours(2))
            .build();

        DocumentWorkflowTransition approvedStart = DocumentWorkflowTransition.of()
            .documentId("doc-approved")
            .toStatus(DocumentWorkflowStatus.PENDING_REVIEW)
            .changedAt(now.minusHours(10))
            .build();

        DocumentWorkflowTransition approvedEnd = DocumentWorkflowTransition.of()
            .documentId("doc-approved")
            .toStatus(DocumentWorkflowStatus.APPROVED)
            .changedAt(now.minusHours(4))
            .build();

        when(workflowTransitionRepository.findByTenantIdOrderByChangedAtDesc("tenant-a"))
            .thenReturn(List.of(approvedEnd, approvedStart, pendingTransition));

        WorkflowDashboardResponse response = workflowQueryService.getDashboardMetrics();

        assertNotNull(response.getSlaReview());
        assertEquals(24, response.getSlaReview().getTargetHours());
        assertEquals(1, response.getSlaReview().getWithinSla());
        assertEquals(0, response.getSlaReview().getOutsideSla());
        assertNotNull(response.getAverageProcessingTimeHours());
        assertEquals(6.0, response.getAverageProcessingTimeHours());
    }
}
