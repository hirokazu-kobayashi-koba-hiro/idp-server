package org.idp.sample.presentation.api.management;

import org.idp.sample.application.service.InitialRegistrationService;
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
@RequestMapping("/{tenant-id}/api/v1/management/initial-registration")
public class InitialRegistrationManagementV1Api implements ParameterTransformable {

  PublicTenantDomain publicTenantDomain;
  InitialRegistrationService initialRegistrationService;

  public InitialRegistrationManagementV1Api(
      InitialRegistrationService initialRegistrationService,
      @Value("${idp.configurations.publicTenantDomain}") String publicTenantDomainValue) {
    this.initialRegistrationService = initialRegistrationService;
    this.publicTenantDomain = new PublicTenantDomain(publicTenantDomainValue);
  }

  @PostMapping
  public ResponseEntity<?> post(
      @AuthenticationPrincipal User operator,
      @PathVariable("tenant-id") TenantIdentifier adminTenantIdentifier,
      @Validated @RequestBody InitialRegistrationRequest request) {

    initialRegistrationService.initialize(
        operator,
        adminTenantIdentifier,
        request.organizationName(),
        publicTenantDomain,
        request.tenantName(),
        request.serverConfig());

    HttpHeaders headers = new HttpHeaders();
    headers.add("Content-Type", "application/json");
    return new ResponseEntity<>(headers, HttpStatus.OK);
  }
}
