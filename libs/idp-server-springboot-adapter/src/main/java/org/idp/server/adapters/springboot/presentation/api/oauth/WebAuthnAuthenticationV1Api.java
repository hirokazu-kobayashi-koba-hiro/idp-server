package org.idp.server.adapters.springboot.presentation.api.oauth;

import java.util.Map;
import org.idp.server.adapters.springboot.application.service.OAuthFlowService;
import org.idp.server.core.oauth.interaction.OAuthUserInteractionResult;
import org.idp.server.core.oauth.interaction.OAuthUserInteractionType;
import org.idp.server.core.tenant.TenantIdentifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/{tenant-id}/api/v1/authorizations/{id}/webauthn/authentication")
public class WebAuthnAuthenticationV1Api {

  OAuthFlowService oAuthFlowService;

  public WebAuthnAuthenticationV1Api(OAuthFlowService oAuthFlowService) {
    this.oAuthFlowService = oAuthFlowService;
  }

  @GetMapping("/challenge")
  public ResponseEntity<?> challenge(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier, @PathVariable("id") String id) {

    OAuthUserInteractionResult result =
        oAuthFlowService.interact(tenantIdentifier, id, OAuthUserInteractionType.WEBAUTHN_AUTHENTICATION_CHALLENGE, Map.of());

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");

    return new ResponseEntity<>(result.response(), httpHeaders, HttpStatus.OK);
  }

  @PostMapping("/response")
  public ResponseEntity<?> authenticate(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("id") String id,
      @RequestBody String request) {

    OAuthUserInteractionResult result = oAuthFlowService.interact(tenantIdentifier, id, OAuthUserInteractionType.WEBAUTHN_AUTHENTICATION, Map.of("request", request));

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");

    return new ResponseEntity<>(result.response(), httpHeaders, HttpStatus.OK);
  }
}
