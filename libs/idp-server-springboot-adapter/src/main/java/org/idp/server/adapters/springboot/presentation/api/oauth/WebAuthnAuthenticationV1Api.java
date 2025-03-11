package org.idp.server.adapters.springboot.presentation.api.oauth;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.adapters.springboot.application.service.OAuthFlowService;
import org.idp.server.adapters.springboot.domain.model.tenant.TenantIdentifier;
import org.idp.server.adapters.springboot.subdomain.webauthn.WebAuthnSession;
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
  public ResponseEntity<Map<String, String>> challenge(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier, @PathVariable("id") String id) {

    WebAuthnSession session =
        oAuthFlowService.challengeWebAuthnAuthentication(tenantIdentifier, id);

    Map<String, String> response = new HashMap<>();
    response.put("challenge", session.challengeAsString());

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");

    return new ResponseEntity<>(response, httpHeaders, HttpStatus.OK);
  }

  @PostMapping("/response")
  public ResponseEntity<String> authenticate(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("id") String id,
      @RequestBody String request) {

    oAuthFlowService.verifyWebAuthnAuthentication(tenantIdentifier, id, request);

    return new ResponseEntity<>(HttpStatus.OK);
  }
}
