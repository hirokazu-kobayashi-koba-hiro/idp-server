package org.idp.server.adapters.springboot.restapi.oauth;

import java.util.Map;

import org.idp.server.core.adapters.IdpServerApplication;
import org.idp.server.core.api.OAuthFlowApi;
import org.idp.server.core.authentication.MfaInteractionResult;
import org.idp.server.core.authentication.StandardMfaInteractionType;
import org.idp.server.core.tenant.TenantIdentifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/{tenant-id}/api/v1/authorizations/{id}/email-verification")
public class EmailVerificationV1Api {

  OAuthFlowApi oAuthFlowApi;

  public EmailVerificationV1Api(IdpServerApplication idpServerApplication) {
    this.oAuthFlowApi = idpServerApplication.oAuthFlowFunction();
  }

  @PostMapping("/challenge")
  public ResponseEntity<?> challenge(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier, @PathVariable("id") String id) {

    MfaInteractionResult result = oAuthFlowApi.interact(tenantIdentifier, id, StandardMfaInteractionType.EMAIL_VERIFICATION_CHALLENGE.toType(), Map.of());

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");

    return new ResponseEntity<>(result.response(), httpHeaders, HttpStatus.OK);
  }

  @PostMapping("/verify")
  public ResponseEntity<?> register(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("id") String id,
      @RequestBody Map<String, Object> params) {


    MfaInteractionResult result = oAuthFlowApi.interact(tenantIdentifier, id, StandardMfaInteractionType.EMAIL_VERIFICATION.toType(), params);

    return new ResponseEntity<>(result.response(), HttpStatus.OK);
  }
}
