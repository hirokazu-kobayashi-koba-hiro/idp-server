package org.idp.server.adapters.springboot.restapi.authentication.device;

import jakarta.servlet.http.HttpServletRequest;
import org.idp.server.adapters.springboot.restapi.ParameterTransformable;
import org.idp.server.core.IdpServerApplication;
import org.idp.server.core.authentication.device.AuthenticationDeviceApi;
import org.idp.server.core.authentication.device.AuthenticationTransactionFindingResponse;
import org.idp.server.core.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.basic.type.security.RequestAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(
    "{tenant-id}/api/v1/authentication-devices/{authentication-device-id}/authentications")
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
