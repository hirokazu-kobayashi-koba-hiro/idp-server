package org.idp.server.adapters.springboot.restapi.management;

import org.idp.server.core.OnboardingEntryService;
import org.idp.server.core.adapters.IdpServerApplication;
import org.idp.server.core.api.OnboardingApi;
import org.idp.server.core.tenant.ServerDomain;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.organization.Organization;
import org.idp.server.adapters.springboot.restapi.ParameterTransformable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/management/onboarding")
public class OnboardingV1Api implements ParameterTransformable {

  ServerDomain serverDomain;
  OnboardingApi onboardingApi;

  public OnboardingV1Api(
      IdpServerApplication idpServerApplication,
      @Value("${idp.configurations.serverUrl}") String idpServerDomainDomain) {
    this.onboardingApi = idpServerApplication.onboardingFunction();
    this.serverDomain = new ServerDomain(idpServerDomainDomain);
  }

  @PostMapping
  public ResponseEntity<?> post(
      @AuthenticationPrincipal User operator,
      @RequestBody Map<String, Object> request) {

    Map<String, Object> response =
        onboardingApi.initialize(
            operator,
            request);

    HttpHeaders headers = new HttpHeaders();
    headers.add("Content-Type", "application/json");
    return new ResponseEntity<>(response, headers, HttpStatus.OK);
  }
}
