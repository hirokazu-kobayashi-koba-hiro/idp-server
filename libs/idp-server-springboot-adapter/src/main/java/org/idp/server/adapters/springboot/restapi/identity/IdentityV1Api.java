package org.idp.server.adapters.springboot.restapi.identity;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.idp.server.adapters.springboot.restapi.ParameterTransformable;
import org.idp.server.core.IdpServerApplication;
import org.idp.server.core.identity.trustframework.*;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.security.RequestAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("{tenant-id}/api/v1/identity/{verification-type}/{verification-process}")
public class IdentityV1Api implements ParameterTransformable {

  IdentityVerificationApi identityVerificationApi;

  public IdentityV1Api(IdpServerApplication idpServerApplication) {
    this.identityVerificationApi = idpServerApplication.identityVerificationApi();
  }

  @PostMapping
  public ResponseEntity<?> apply(
      @RequestHeader(required = false, value = "Authorization") String authorizationHeader,
      @RequestHeader(required = false, value = "x-ssl-cert") String clientCert,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("verification-type") IdentityVerificationType verificationType,
      @PathVariable("verification-process") VerificationProcess verificationProcess,
      @RequestBody(required = false) Map<String, Object> requestBody,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    IdentityVerificationApplicationResponse response =
        identityVerificationApi.apply(
            tenantIdentifier,
            verificationType,
            verificationProcess,
            new IdentityVerificationApplicationRequest(requestBody),
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");
    return new ResponseEntity<>(
        response.response(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }
}
