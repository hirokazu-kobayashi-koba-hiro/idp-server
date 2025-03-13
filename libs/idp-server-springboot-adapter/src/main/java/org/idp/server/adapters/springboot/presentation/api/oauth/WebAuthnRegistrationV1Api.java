package org.idp.server.adapters.springboot.presentation.api.oauth;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.adapters.springboot.application.service.OAuthFlowService;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.authenticators.webauthn.WebAuthnSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/{tenant-id}/api/v1/authorizations/{id}/webauthn/registration")
public class WebAuthnRegistrationV1Api {

  OAuthFlowService oAuthFlowService;

  public WebAuthnRegistrationV1Api(OAuthFlowService oAuthFlowService) {
    this.oAuthFlowService = oAuthFlowService;
  }

  @GetMapping("/challenge")
  public ResponseEntity<Map<String, String>> getChallenge(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier, @PathVariable("id") String id) {

    WebAuthnSession webAuthnSession =
        oAuthFlowService.challengeWebAuthnRegistration(tenantIdentifier, id);

    Map<String, String> response = new HashMap<>();
    response.put("challenge", (webAuthnSession.challengeAsString()));

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");

    return new ResponseEntity<>(response, httpHeaders, HttpStatus.OK);
  }

  @PostMapping("/response")
  public ResponseEntity<String> register(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("id") String id,
      @RequestBody String request) {

    oAuthFlowService.verifyWebAuthnRegistration(tenantIdentifier, id, request);

    return new ResponseEntity<>(HttpStatus.OK);
  }
}
