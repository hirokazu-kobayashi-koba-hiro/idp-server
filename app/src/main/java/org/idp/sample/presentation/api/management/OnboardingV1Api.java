package org.idp.sample.presentation.api.management;

import org.idp.sample.application.service.OnboardingService;
import org.idp.sample.domain.model.organization.Organization;
import org.idp.sample.domain.model.tenant.*;
import org.idp.sample.presentation.api.ParameterTransformable;
import org.idp.server.oauth.identity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/management/onboarding")
public class OnboardingV1Api implements ParameterTransformable {

  PublicTenantDomain publicTenantDomain;
  OnboardingService onboardingService;

  public OnboardingV1Api(
      OnboardingService onboardingService,
      @Value("${idp.configurations.publicTenantDomain}") String publicTenantDomainValue) {
    this.onboardingService = onboardingService;
    this.publicTenantDomain = new PublicTenantDomain(publicTenantDomainValue);
  }

  @PostMapping
  public ResponseEntity<?> post(
      @AuthenticationPrincipal User operator,
      @Validated @RequestBody InitialRegistrationRequest request) {

    Organization organization =
        onboardingService.initialize(
            operator,
            request.organizationName(),
            publicTenantDomain,
            request.tenantName(),
            request.serverConfig());

    HttpHeaders headers = new HttpHeaders();
    headers.add("Content-Type", "application/json");
    return new ResponseEntity<>(organization.toMap(), headers, HttpStatus.OK);
  }
}
