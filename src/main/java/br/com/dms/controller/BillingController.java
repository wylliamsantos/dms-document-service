package br.com.dms.controller;

import br.com.dms.controller.request.BillingWebhookRequest;
import br.com.dms.controller.response.BillingSubscriptionResponse;
import br.com.dms.service.BillingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/billing")
@Slf4j
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @GetMapping("/subscription")
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN','ROLE_REVIEWER','ROLE_VIEWER','ROLE_DOCUMENT_VIEWER')")
    public ResponseEntity<BillingSubscriptionResponse> getCurrentSubscription(@RequestHeader(name = "TransactionId") String transactionId) {
        log.info("DMS version v1 - BillingSubscription - transactionId: {}", transactionId);
        return new ResponseEntity<>(billingService.getOrStartTrialForAuthenticatedTenant(), HttpStatus.OK);
    }

    @PostMapping("/webhook")
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
    public ResponseEntity<BillingSubscriptionResponse> applyWebhook(@RequestHeader(name = "TransactionId") String transactionId,
                                                                    @RequestBody BillingWebhookRequest request) {
        log.info("DMS version v1 - BillingWebhook - transactionId: {}, eventId: {}", transactionId, request.getEventId());
        return new ResponseEntity<>(billingService.applyWebhook(request), HttpStatus.OK);
    }
}
