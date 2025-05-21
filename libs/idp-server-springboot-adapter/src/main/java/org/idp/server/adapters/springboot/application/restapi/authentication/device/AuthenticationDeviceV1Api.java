package org.idp.server.adapters.springboot.application.restapi.authentication.device;

import jakarta.servlet.http.HttpServletRequest;
import org.idp.server.IdpServerApplication;
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.authentication.interactors.device.AuthenticationDeviceApi;
import org.idp.server.authentication.interactors.device.AuthenticationTransactionFindingResponse;
import org.idp.server.core.oidc.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.security.type.RequestAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("{tenant-id}/v1/authentication-devices/{authentication-device-id}/authentications")
public class AuthenticationDeviceV1Api implements ParameterTransformable {

  AuthenticationDeviceApi authenticationDeviceApi;

  public AuthenticationDeviceV1Api(IdpServerApplication idpServerApplication) {
    this.authenticationDeviceApi = idpServerApplication.authenticationDeviceApi();
  }

  @GetMapping("/latest")
  public ResponseEntity<?> get(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("authentication-device-id")
          AuthenticationDeviceIdentifier authenticationDeviceIdentifier,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    AuthenticationTransactionFindingResponse response =
        authenticationDeviceApi.findLatest(
            tenantIdentifier, authenticationDeviceIdentifier, requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }
}
