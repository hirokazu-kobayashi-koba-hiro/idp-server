package org.idp.server.adapters.springboot.restapi.management;

import java.util.Map;
import org.idp.server.IdpServerApplication;
import org.idp.server.adapters.springboot.restapi.ParameterTransformable;
import org.idp.server.control_plane.OnboardingApi;
import org.idp.server.core.identity.User;
import org.idp.server.core.multi_tenancy.tenant.AdminTenantContext;
import org.idp.server.core.multi_tenancy.tenant.ServerDomain;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/management/onboarding")
public class OnboardingV1Api implements ParameterTransformable {

  ServerDomain serverDomain;
  OnboardingApi onboardingApi;

  public OnboardingV1Api(
      IdpServerApplication idpServerApplication,
      @Value("${idp.configurations.serverUrl}") String idpServerDomainDomain) {
    this.onboardingApi = idpServerApplication.onboardingApi();
    this.serverDomain = new ServerDomain(idpServerDomainDomain);
  }

  @PostMapping
  public ResponseEntity<?> post(
      @AuthenticationPrincipal User operator, @RequestBody Map<String, Object> request) {

    TenantIdentifier adminTenantIdentifier = AdminTenantContext.getTenantIdentifier();
    Map<String, Object> response =
        onboardingApi.initialize(adminTenantIdentifier, operator, request);

    HttpHeaders headers = new HttpHeaders();
    headers.add("Content-Type", "application/json");
    return new ResponseEntity<>(response, headers, HttpStatus.OK);
  }
}
