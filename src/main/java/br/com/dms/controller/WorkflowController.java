package br.com.dms.controller;

import br.com.dms.controller.request.WorkflowReviewRequest;
import br.com.dms.controller.response.PendingDocumentResponse;
import br.com.dms.controller.response.WorkflowReviewResponse;
import br.com.dms.controller.response.WorkflowTransitionResponse;
import br.com.dms.service.DmsService;
import br.com.dms.service.WorkflowQueryService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/v1/workflow")
@PreAuthorize("hasAuthority('ROLE_DOCUMENT_VIEWER')")
public class WorkflowController {

    private final WorkflowQueryService workflowQueryService;
    private final DmsService dmsService;

    public WorkflowController(WorkflowQueryService workflowQueryService, DmsService dmsService) {
        this.workflowQueryService = workflowQueryService;
        this.dmsService = dmsService;
    }

    @GetMapping(path = "/documents/{documentId}/history", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<WorkflowTransitionResponse>> getDocumentHistory(
        @RequestHeader(name = "TransactionId") String transactionId,
        @RequestHeader(name = "Authorization") String authorization,
        @PathVariable("documentId") String documentId) {

        return ResponseEntity.ok(workflowQueryService.listDocumentHistory(documentId));
    }

    @GetMapping(path = "/pending", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<PendingDocumentResponse>> listPending(
        @RequestHeader(name = "TransactionId") String transactionId,
        @RequestHeader(name = "Authorization") String authorization,
        @RequestParam(name = "category", required = false) String category,
        @RequestParam(name = "author", required = false) String author,
        @RequestParam(name = "from", required = false) LocalDateTime from,
        @RequestParam(name = "to", required = false) LocalDateTime to,
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(name = "size", defaultValue = "20") int size) {

        return ResponseEntity.ok(workflowQueryService.listPendingReview(category, author, from, to, page, size));
    }

    @PostMapping(path = "/documents/{documentId}/review", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<WorkflowReviewResponse> reviewDocument(
        @RequestHeader(name = "TransactionId") String transactionId,
        @RequestHeader(name = "Authorization") String authorization,
        @RequestHeader(name = "Actor", required = false) String actor,
        @PathVariable("documentId") String documentId,
        @RequestBody @Valid WorkflowReviewRequest request) {

        var status = dmsService.reviewDocumentWorkflow(transactionId, documentId, request.getAction(), request.getReason(), actor);

        WorkflowReviewResponse response = new WorkflowReviewResponse();
        response.setDocumentId(documentId);
        response.setWorkflowStatus(status);
        response.setMessage("Workflow updated");
        return ResponseEntity.ok(response);
    }
}
