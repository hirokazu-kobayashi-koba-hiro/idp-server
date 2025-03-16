package org.idp.server.adapters.springboot.restapi.oauth;

import java.util.Map;

import org.idp.server.core.adapters.IdpServerApplication;
import org.idp.server.core.function.OAuthFlowFunction;
import org.idp.server.core.oauth.interaction.OAuthUserInteractionResult;
import org.idp.server.core.oauth.interaction.OAuthUserInteractionType;
import org.idp.server.core.tenant.TenantIdentifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/{tenant-id}/api/v1/authorizations/{id}/email-verification")
public class EmailVerificationV1Api {

  OAuthFlowFunction oAuthFlowFunction;

  public EmailVerificationV1Api(IdpServerApplication idpServerApplication) {
    this.oAuthFlowFunction = idpServerApplication.oAuthFlowFunction();
  }

  @PostMapping("/challenge")
  public ResponseEntity<?> challenge(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier, @PathVariable("id") String id) {

    OAuthUserInteractionResult result = oAuthFlowFunction.interact(tenantIdentifier, id, OAuthUserInteractionType.EMAIL_VERIFICATION_CHALLENGE, Map.of());

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");

    return new ResponseEntity<>(result.response(), httpHeaders, HttpStatus.OK);
  }

  @PostMapping("/verify")
  public ResponseEntity<?> register(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("id") String id,
      @RequestBody Map<String, Object> params) {


    OAuthUserInteractionResult result = oAuthFlowFunction.interact(tenantIdentifier, id, OAuthUserInteractionType.EMAIL_VERIFICATION, params);

    return new ResponseEntity<>(result.response(), HttpStatus.OK);
  }
}
