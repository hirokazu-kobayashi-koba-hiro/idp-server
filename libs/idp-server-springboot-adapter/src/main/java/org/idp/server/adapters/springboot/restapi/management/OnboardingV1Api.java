package org.idp.server.adapters.springboot.restapi.management;

import java.util.Map;
import org.idp.server.IdpServerApplication;
import org.idp.server.adapters.springboot.restapi.ParameterTransformable;
import org.idp.server.control_plane.onboarding.OnboardingApi;
import org.idp.server.control_plane.onboarding.io.OnboardingRequest;
import org.idp.server.control_plane.onboarding.io.OnboardingResponse;
import org.idp.server.core.identity.User;
import org.idp.server.core.multi_tenancy.tenant.AdminTenantContext;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/management/onboarding")
public class OnboardingV1Api implements ParameterTransformable {

  OnboardingApi onboardingApi;

  public OnboardingV1Api(IdpServerApplication idpServerApplication) {
    this.onboardingApi = idpServerApplication.onboardingApi();
  }

  @PostMapping
  public ResponseEntity<?> post(
      @AuthenticationPrincipal User operator, @RequestBody Map<String, Object> request) {

    TenantIdentifier adminTenantIdentifier = AdminTenantContext.getTenantIdentifier();
    OnboardingResponse response =
        onboardingApi.onboard(adminTenantIdentifier, operator, new OnboardingRequest(request));

    HttpHeaders headers = new HttpHeaders();
    headers.add("Content-Type", "application/json");
    return new ResponseEntity<>(response, headers, HttpStatus.OK);
  }
}
