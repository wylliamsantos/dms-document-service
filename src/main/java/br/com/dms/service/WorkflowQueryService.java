package br.com.dms.service;

import br.com.dms.controller.response.PendingDocumentResponse;
import br.com.dms.controller.response.WorkflowCategoryStatusCountResponse;
import br.com.dms.controller.response.WorkflowDashboardResponse;
import br.com.dms.controller.response.WorkflowStatusCountResponse;
import br.com.dms.controller.response.WorkflowSlaReviewResponse;
import br.com.dms.controller.response.WorkflowTransitionResponse;
import br.com.dms.domain.core.DocumentWorkflowStatus;
import br.com.dms.domain.mongodb.DmsDocument;
import br.com.dms.domain.mongodb.DmsDocumentVersion;
import br.com.dms.domain.mongodb.DocumentWorkflowTransition;
import br.com.dms.repository.mongo.DmsDocumentRepository;
import br.com.dms.repository.mongo.DmsDocumentVersionRepository;
import br.com.dms.repository.mongo.DocumentWorkflowTransitionRepository;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class WorkflowQueryService {

    private final DmsDocumentRepository dmsDocumentRepository;
    private final DmsDocumentVersionRepository dmsDocumentVersionRepository;
    private final DocumentWorkflowTransitionRepository workflowTransitionRepository;
    private final TenantContextService tenantContextService;

    public WorkflowQueryService(DmsDocumentRepository dmsDocumentRepository,
                                DmsDocumentVersionRepository dmsDocumentVersionRepository,
                                DocumentWorkflowTransitionRepository workflowTransitionRepository,
                                TenantContextService tenantContextService) {
        this.dmsDocumentRepository = dmsDocumentRepository;
        this.dmsDocumentVersionRepository = dmsDocumentVersionRepository;
        this.workflowTransitionRepository = workflowTransitionRepository;
        this.tenantContextService = tenantContextService;
    }

    public WorkflowDashboardResponse getDashboardMetrics() {
        String tenantId = tenantContextService.requireTenantId();
        List<DmsDocument> documents = dmsDocumentRepository.findByTenantId(tenantId);

        Map<DocumentWorkflowStatus, Long> statusCounter = documents.stream()
            .collect(Collectors.groupingBy(
                document -> document.getWorkflowStatus() == null ? DocumentWorkflowStatus.DRAFT : document.getWorkflowStatus(),
                Collectors.counting()));

        List<WorkflowStatusCountResponse> statusCounts = statusCounter.entrySet().stream()
            .map(entry -> new WorkflowStatusCountResponse(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparing(item -> item.getStatus().name()))
            .collect(Collectors.toList());

        Map<String, Map<DocumentWorkflowStatus, Long>> categoryCounter = documents.stream()
            .collect(Collectors.groupingBy(
                document -> StringUtils.defaultIfBlank(document.getCategory(), "SEM_CATEGORIA"),
                Collectors.groupingBy(
                    document -> document.getWorkflowStatus() == null ? DocumentWorkflowStatus.DRAFT : document.getWorkflowStatus(),
                    Collectors.counting())));

        List<WorkflowCategoryStatusCountResponse> categoryStatusCounts = categoryCounter.entrySet().stream()
            .flatMap(categoryEntry -> categoryEntry.getValue().entrySet().stream()
                .map(statusEntry -> new WorkflowCategoryStatusCountResponse(categoryEntry.getKey(), statusEntry.getKey(), statusEntry.getValue())))
            .sorted(Comparator.comparing(WorkflowCategoryStatusCountResponse::getCategory)
                .thenComparing(item -> item.getStatus().name()))
            .collect(Collectors.toList());

        List<DocumentWorkflowTransition> transitions = workflowTransitionRepository.findByTenantIdOrderByChangedAtDesc(tenantId);
        Map<String, List<DocumentWorkflowTransition>> transitionsByDocument = transitions.stream()
            .collect(Collectors.groupingBy(DocumentWorkflowTransition::getDocumentId));

        WorkflowSlaReviewResponse slaReview = buildSlaReview(documents, transitionsByDocument);
        Double averageProcessingTimeHours = calculateAverageProcessingTimeHours(transitionsByDocument);

        WorkflowDashboardResponse response = new WorkflowDashboardResponse();
        response.setTotalDocuments(documents.size());
        response.setStatusCounts(statusCounts);
        response.setCategoryStatusCounts(categoryStatusCounts);
        response.setSlaReview(slaReview);
        response.setAverageProcessingTimeHours(averageProcessingTimeHours);
        return response;
    }

    private WorkflowSlaReviewResponse buildSlaReview(List<DmsDocument> documents,
                                                     Map<String, List<DocumentWorkflowTransition>> transitionsByDocument) {
        final int targetHours = 24;
        LocalDateTime now = LocalDateTime.now();

        long withinSla = 0;
        long outsideSla = 0;

        for (DmsDocument document : documents) {
            if (document.getWorkflowStatus() != DocumentWorkflowStatus.PENDING_REVIEW) {
                continue;
            }

            LocalDateTime pendingSince = transitionsByDocument
                .getOrDefault(document.getId(), List.of())
                .stream()
                .filter(transition -> transition.getToStatus() == DocumentWorkflowStatus.PENDING_REVIEW)
                .map(DocumentWorkflowTransition::getChangedAt)
                .filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);

            if (pendingSince == null) {
                outsideSla++;
                continue;
            }

            long elapsedHours = Duration.between(pendingSince, now).toHours();
            if (elapsedHours <= targetHours) {
                withinSla++;
            } else {
                outsideSla++;
            }
        }

        return new WorkflowSlaReviewResponse(targetHours, withinSla, outsideSla);
    }

    private Double calculateAverageProcessingTimeHours(Map<String, List<DocumentWorkflowTransition>> transitionsByDocument) {
        List<Long> processingTimesHours = transitionsByDocument.values().stream()
            .map(this::calculateProcessingHours)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toList());

        if (processingTimesHours.isEmpty()) {
            return null;
        }

        return processingTimesHours.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0.0);
    }

    private Long calculateProcessingHours(List<DocumentWorkflowTransition> transitions) {
        List<DocumentWorkflowTransition> sorted = transitions.stream()
            .sorted(Comparator.comparing(DocumentWorkflowTransition::getChangedAt, Comparator.nullsLast(Comparator.naturalOrder())))
            .collect(Collectors.toList());

        LocalDateTime startedAt = sorted.stream()
            .filter(transition -> transition.getToStatus() == DocumentWorkflowStatus.PENDING_REVIEW)
            .map(DocumentWorkflowTransition::getChangedAt)
            .filter(java.util.Objects::nonNull)
            .findFirst()
            .orElse(null);

        LocalDateTime finishedAt = sorted.stream()
            .filter(transition -> transition.getToStatus() == DocumentWorkflowStatus.APPROVED
                || transition.getToStatus() == DocumentWorkflowStatus.REJECTED)
            .map(DocumentWorkflowTransition::getChangedAt)
            .filter(java.util.Objects::nonNull)
            .findFirst()
            .orElse(null);

        if (startedAt == null || finishedAt == null || finishedAt.isBefore(startedAt)) {
            return null;
        }

        return Duration.between(startedAt, finishedAt).toHours();
    }

    public List<WorkflowTransitionResponse> listDocumentHistory(String documentId) {
        String tenantId = tenantContextService.requireTenantId();
        return workflowTransitionRepository.findByTenantIdAndDocumentIdOrderByChangedAtDesc(tenantId, documentId)
            .stream()
            .map(transition -> {
                WorkflowTransitionResponse response = new WorkflowTransitionResponse();
                response.setFromStatus(transition.getFromStatus());
                response.setToStatus(transition.getToStatus());
                response.setActor(transition.getActor());
                response.setReason(transition.getReason());
                response.setChangedAt(transition.getChangedAt());
                return response;
            })
            .collect(Collectors.toList());
    }

    public Page<PendingDocumentResponse> listPendingReview(String category,
                                                           String author,
                                                           LocalDateTime from,
                                                           LocalDateTime to,
                                                           int page,
                                                           int size) {
        String tenantId = tenantContextService.requireTenantId();
        List<DmsDocument> documents = StringUtils.isBlank(category)
            ? dmsDocumentRepository.findByTenantIdAndWorkflowStatus(tenantId, DocumentWorkflowStatus.PENDING_REVIEW)
            : dmsDocumentRepository.findByTenantIdAndWorkflowStatusAndCategory(tenantId, DocumentWorkflowStatus.PENDING_REVIEW, category);

        List<PendingDocumentResponse> items = new ArrayList<>();
        for (DmsDocument document : documents) {
            Optional<DmsDocumentVersion> versionOpt = dmsDocumentVersionRepository.findLastVersionByTenantIdAndDmsDocumentId(tenantId, document.getId());
            if (versionOpt.isEmpty()) {
                continue;
            }

            DmsDocumentVersion version = versionOpt.get();

            if (StringUtils.isNotBlank(author) && !StringUtils.equalsIgnoreCase(StringUtils.defaultString(version.getAuthor()), author)) {
                continue;
            }

            LocalDateTime updatedAt = version.getModifiedAt();
            if (from != null && (updatedAt == null || updatedAt.isBefore(from))) {
                continue;
            }
            if (to != null && (updatedAt == null || updatedAt.isAfter(to))) {
                continue;
            }

            PendingDocumentResponse response = new PendingDocumentResponse();
            response.setDocumentId(document.getId());
            response.setFilename(document.getFilename());
            response.setCategory(document.getCategory());
            response.setWorkflowStatus(document.getWorkflowStatus());
            response.setCurrentVersion(version.getVersionNumber() != null ? version.getVersionNumber().toPlainString() : null);
            response.setAuthor(version.getAuthor());
            response.setBusinessKeyType(document.getBusinessKeyType());
            response.setBusinessKeyValue(document.getBusinessKeyValue());
            response.setUpdatedAt(updatedAt);
            items.add(response);
        }

        items.sort(Comparator.comparing(PendingDocumentResponse::getUpdatedAt,
            Comparator.nullsLast(Comparator.naturalOrder())).reversed());

        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), items.size());
        List<PendingDocumentResponse> pageItems = start >= items.size() ? List.of() : items.subList(start, end);

        return new PageImpl<>(pageItems, pageable, items.size());
    }
}
