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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    private Clock fixedClock;

    @BeforeEach
    void setup() {
        fixedClock = Clock.fixed(Instant.parse("2026-03-05T08:00:00Z"), ZoneOffset.UTC);
        workflowQueryService = new WorkflowQueryService(
            dmsDocumentRepository,
            dmsDocumentVersionRepository,
            workflowTransitionRepository,
            tenantContextService,
            fixedClock
        );
    }

    @Test
    void shouldReturnSlaAndAverageProcessingMetrics() {
        when(tenantContextService.requireTenantId()).thenReturn("tenant-a");

        DmsDocument pending = DmsDocument.of().id("doc-pending").workflowStatus(DocumentWorkflowStatus.PENDING_REVIEW).build();
        DmsDocument approved = DmsDocument.of().id("doc-approved").workflowStatus(DocumentWorkflowStatus.APPROVED).build();

        when(dmsDocumentRepository.findByTenantId("tenant-a")).thenReturn(List.of(pending, approved));

        LocalDateTime now = LocalDateTime.now(fixedClock);
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

    @Test
    void shouldTreatExactly24HoursAsWithinSla() {
        when(tenantContextService.requireTenantId()).thenReturn("tenant-a");

        DmsDocument pending = DmsDocument.of().id("doc-pending").workflowStatus(DocumentWorkflowStatus.PENDING_REVIEW).build();
        when(dmsDocumentRepository.findByTenantId("tenant-a")).thenReturn(List.of(pending));

        LocalDateTime now = LocalDateTime.now(fixedClock);
        DocumentWorkflowTransition pendingTransition = DocumentWorkflowTransition.of()
            .documentId("doc-pending")
            .toStatus(DocumentWorkflowStatus.PENDING_REVIEW)
            .changedAt(now.minusHours(24))
            .build();

        when(workflowTransitionRepository.findByTenantIdOrderByChangedAtDesc("tenant-a"))
            .thenReturn(List.of(pendingTransition));

        WorkflowDashboardResponse response = workflowQueryService.getDashboardMetrics();

        assertEquals(1, response.getSlaReview().getWithinSla());
        assertEquals(0, response.getSlaReview().getOutsideSla());
    }

    @Test
    void shouldIgnoreInvalidProcessingIntervalsFromAverage() {
        when(tenantContextService.requireTenantId()).thenReturn("tenant-a");

        DmsDocument approved = DmsDocument.of().id("doc-approved").workflowStatus(DocumentWorkflowStatus.APPROVED).build();
        when(dmsDocumentRepository.findByTenantId("tenant-a")).thenReturn(List.of(approved));

        LocalDateTime now = LocalDateTime.now(fixedClock);
        DocumentWorkflowTransition approvedEndBeforeStart = DocumentWorkflowTransition.of()
            .documentId("doc-approved")
            .toStatus(DocumentWorkflowStatus.APPROVED)
            .changedAt(now.minusHours(8))
            .build();

        DocumentWorkflowTransition approvedStart = DocumentWorkflowTransition.of()
            .documentId("doc-approved")
            .toStatus(DocumentWorkflowStatus.PENDING_REVIEW)
            .changedAt(now.minusHours(2))
            .build();

        when(workflowTransitionRepository.findByTenantIdOrderByChangedAtDesc("tenant-a"))
            .thenReturn(List.of(approvedEndBeforeStart, approvedStart));

        WorkflowDashboardResponse response = workflowQueryService.getDashboardMetrics();

        assertEquals(0, response.getSlaReview().getWithinSla());
        assertEquals(0, response.getSlaReview().getOutsideSla());
        assertNull(response.getAverageProcessingTimeHours());
    }
}
