package br.com.dms.service;

import br.com.dms.controller.response.PendingDocumentResponse;
import br.com.dms.controller.response.WorkflowTransitionResponse;
import br.com.dms.domain.core.DocumentWorkflowStatus;
import br.com.dms.domain.mongodb.DmsDocument;
import br.com.dms.domain.mongodb.DmsDocumentVersion;
import br.com.dms.repository.mongo.DmsDocumentRepository;
import br.com.dms.repository.mongo.DmsDocumentVersionRepository;
import br.com.dms.repository.mongo.DocumentWorkflowTransitionRepository;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class WorkflowQueryService {

    private final DmsDocumentRepository dmsDocumentRepository;
    private final DmsDocumentVersionRepository dmsDocumentVersionRepository;
    private final DocumentWorkflowTransitionRepository workflowTransitionRepository;

    public WorkflowQueryService(DmsDocumentRepository dmsDocumentRepository,
                                DmsDocumentVersionRepository dmsDocumentVersionRepository,
                                DocumentWorkflowTransitionRepository workflowTransitionRepository) {
        this.dmsDocumentRepository = dmsDocumentRepository;
        this.dmsDocumentVersionRepository = dmsDocumentVersionRepository;
        this.workflowTransitionRepository = workflowTransitionRepository;
    }

    public List<WorkflowTransitionResponse> listDocumentHistory(String documentId) {
        return workflowTransitionRepository.findByDocumentIdOrderByChangedAtDesc(documentId)
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
        List<DmsDocument> documents = StringUtils.isBlank(category)
            ? dmsDocumentRepository.findByWorkflowStatus(DocumentWorkflowStatus.PENDING_REVIEW)
            : dmsDocumentRepository.findByWorkflowStatusAndCategory(DocumentWorkflowStatus.PENDING_REVIEW, category);

        List<PendingDocumentResponse> items = new ArrayList<>();
        for (DmsDocument document : documents) {
            Optional<DmsDocumentVersion> versionOpt = dmsDocumentVersionRepository.findLastVersionByDmsDocumentId(document.getId());
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
