package org.idp.server.adapters.springboot.presentation.api.oauth;

import java.util.Map;
import org.idp.server.adapters.springboot.application.service.OAuthFlowService;
import org.idp.server.core.tenant.TenantIdentifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/{tenant-id}/api/v1/authorizations/{id}/email-verification")
public class EmailVerificationV1Api {

  OAuthFlowService oAuthFlowService;

  public EmailVerificationV1Api(OAuthFlowService oAuthFlowService) {
    this.oAuthFlowService = oAuthFlowService;
  }

  @PostMapping("/challenge")
  public ResponseEntity<Map<String, String>> challenge(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier, @PathVariable("id") String id) {

    oAuthFlowService.challengeEmailVerification(tenantIdentifier, id);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");

    return new ResponseEntity<>(httpHeaders, HttpStatus.OK);
  }

  @PostMapping("/verify")
  public ResponseEntity<String> register(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("id") String id,
      @RequestBody Map<String, String> request) {

    String verificationCode = request.getOrDefault("verification_code", "");

    oAuthFlowService.verifyEmail(tenantIdentifier, id, verificationCode);

    return new ResponseEntity<>(HttpStatus.OK);
  }
}
