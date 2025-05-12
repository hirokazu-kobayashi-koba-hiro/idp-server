package org.idp.server.adapters.springboot.control_plane.restapi.management;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.idp.server.IdpServerApplication;
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.adapters.springboot.control_plane.model.OperatorPrincipal;
import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.control_plane.management.authentication.AuthenticationConfigurationManagementApi;
import org.idp.server.control_plane.management.authentication.io.AuthenticationConfigManagementResponse;
import org.idp.server.control_plane.management.authentication.io.AuthenticationConfigRegistrationRequest;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/management/tenants/{tenant-id}/authentication-configurations")
public class AuthenticationConfigurationManagementV1Api implements ParameterTransformable {

  AuthenticationConfigurationManagementApi authenticationConfigurationManagementApi;

  public AuthenticationConfigurationManagementV1Api(IdpServerApplication idpServerApplication) {
    this.authenticationConfigurationManagementApi =
        idpServerApplication.authenticationConfigurationManagementApi();
  }

  @PostMapping
  public ResponseEntity<?> post(
      @AuthenticationPrincipal OperatorPrincipal operatorPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestBody(required = false) Map<String, Object> body,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    AuthenticationConfigManagementResponse response =
        authenticationConfigurationManagementApi.register(
            tenantIdentifier,
            operatorPrincipal.getUser(),
            operatorPrincipal.getOAuthToken(),
            new AuthenticationConfigRegistrationRequest(body),
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }
}
