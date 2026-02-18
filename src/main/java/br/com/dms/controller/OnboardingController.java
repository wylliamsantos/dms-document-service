package br.com.dms.controller;

import br.com.dms.controller.request.OnboardingBootstrapRequest;
import br.com.dms.controller.response.OnboardingBootstrapResponse;
import br.com.dms.service.OnboardingBootstrapService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static br.com.dms.config.AuthorizationRules.MANAGE;

@RestController
@RequestMapping("/v1/onboarding")
@Slf4j
public class OnboardingController {

    private final OnboardingBootstrapService onboardingBootstrapService;

    public OnboardingController(OnboardingBootstrapService onboardingBootstrapService) {
        this.onboardingBootstrapService = onboardingBootstrapService;
    }

    @PostMapping("/bootstrap")
    @PreAuthorize(MANAGE)
    public ResponseEntity<OnboardingBootstrapResponse> bootstrap(@RequestHeader(name = "TransactionId") String transactionId,
                                                                 @RequestHeader(name = "Authorization") String authorization,
                                                                 @RequestBody @Valid OnboardingBootstrapRequest request) {
        log.info("DMS version v1 - OnboardingBootstrap - transactionId: {}", transactionId);
        return new ResponseEntity<>(onboardingBootstrapService.bootstrap(request), HttpStatus.OK);
    }
}
