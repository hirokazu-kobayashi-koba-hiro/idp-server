package org.idp.server.adapters.springboot.restapi.identity;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.idp.server.adapters.springboot.operation.ResourceOwnerPrincipal;
import org.idp.server.adapters.springboot.restapi.ParameterTransformable;
import org.idp.server.core.IdpServerApplication;
import org.idp.server.core.identity.trustframework.*;
import org.idp.server.core.identity.trustframework.application.IdentityVerificationApplicationIdentifier;
import org.idp.server.core.identity.trustframework.application.IdentityVerificationRequest;
import org.idp.server.core.identity.trustframework.application.IdentityVerificationResponse;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.security.RequestAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/{tenant-id}/api/v1/identity/{verification-type}")
public class IdentityV1Api implements ParameterTransformable {

  IdentityVerificationApi identityVerificationApi;

  public IdentityV1Api(IdpServerApplication idpServerApplication) {
    this.identityVerificationApi = idpServerApplication.identityVerificationApi();
  }

  @PostMapping("/{verification-process}")
  public ResponseEntity<?> apply(
      @AuthenticationPrincipal ResourceOwnerPrincipal resourceOwnerPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("verification-type") IdentityVerificationType verificationType,
      @PathVariable("verification-process") IdentityVerificationProcess identityVerificationProcess,
      @RequestBody(required = false) Map<String, Object> requestBody,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    IdentityVerificationResponse response =
        identityVerificationApi.apply(
            tenantIdentifier,
            resourceOwnerPrincipal.getUser(),
            resourceOwnerPrincipal.getOAuthToken(),
            verificationType,
            identityVerificationProcess,
            new IdentityVerificationRequest(requestBody),
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");
    return new ResponseEntity<>(
        response.response(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @PostMapping("/{id}/{verification-process}")
  public ResponseEntity<?> process(
      @AuthenticationPrincipal ResourceOwnerPrincipal resourceOwnerPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("id") IdentityVerificationApplicationIdentifier identifier,
      @PathVariable("verification-type") IdentityVerificationType verificationType,
      @PathVariable("verification-process") IdentityVerificationProcess identityVerificationProcess,
      @RequestBody(required = false) Map<String, Object> requestBody,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    IdentityVerificationResponse response =
        identityVerificationApi.process(
            tenantIdentifier,
            resourceOwnerPrincipal.getUser(),
            resourceOwnerPrincipal.getOAuthToken(),
            identifier,
            verificationType,
            identityVerificationProcess,
            new IdentityVerificationRequest(requestBody),
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");
    return new ResponseEntity<>(
        response.response(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }
}
