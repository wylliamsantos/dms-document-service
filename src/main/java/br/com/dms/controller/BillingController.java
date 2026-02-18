package br.com.dms.controller;

import br.com.dms.controller.request.BillingWebhookRequest;
import br.com.dms.controller.response.BillingSubscriptionResponse;
import br.com.dms.service.BillingService;
import br.com.dms.service.BillingWebhookSignatureService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/v1/billing")
@Slf4j
public class BillingController {

    private final BillingService billingService;
    private final BillingWebhookSignatureService billingWebhookSignatureService;
    private final ObjectMapper objectMapper;

    public BillingController(BillingService billingService,
                             BillingWebhookSignatureService billingWebhookSignatureService,
                             ObjectMapper objectMapper) {
        this.billingService = billingService;
        this.billingWebhookSignatureService = billingWebhookSignatureService;
        this.objectMapper = objectMapper;
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
                                                                    @RequestHeader(name = "X-Billing-Signature") String signature,
                                                                    @RequestBody String rawPayload) {
        var request = parseWebhookPayload(rawPayload);
        log.info("DMS version v1 - BillingWebhook - transactionId: {}, eventId: {}", transactionId, request.getEventId());

        billingWebhookSignatureService.assertValidSignature(rawPayload, signature);

        return new ResponseEntity<>(billingService.applyWebhook(request), HttpStatus.OK);
    }

    private BillingWebhookRequest parseWebhookPayload(String rawPayload) {
        try {
            return objectMapper.readValue(rawPayload, BillingWebhookRequest.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid billing webhook payload", e);
        }
    }
}
