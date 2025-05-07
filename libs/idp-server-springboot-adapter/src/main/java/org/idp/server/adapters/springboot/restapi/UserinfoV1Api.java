package org.idp.server.adapters.springboot.restapi;

import jakarta.servlet.http.HttpServletRequest;
import org.idp.server.IdpServerApplication;
import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.oidc.userinfo.UserinfoApi;
import org.idp.server.core.oidc.userinfo.handler.io.UserinfoRequestResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("{tenant-id}/api/v1/userinfo")
public class UserinfoV1Api implements ParameterTransformable {

  UserinfoApi userinfoApi;

  public UserinfoV1Api(IdpServerApplication idpServerApplication) {
    this.userinfoApi = idpServerApplication.userinfoApi();
  }

  @GetMapping
  public ResponseEntity<?> get(
      @RequestHeader(required = false, value = "Authorization") String authorizationHeader,
      @RequestHeader(required = false, value = "x-ssl-cert") String clientCert,
      @PathVariable("tenant-id") TenantIdentifier tenantId,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    UserinfoRequestResponse response =
        userinfoApi.request(tenantId, authorizationHeader, clientCert, requestAttributes);

    return new ResponseEntity<>(response.response(), HttpStatus.valueOf(response.statusCode()));
  }

  @PostMapping
  public ResponseEntity<?> post(
      @RequestHeader(required = false, value = "Authorization") String authorizationHeader,
      @RequestHeader(required = false, value = "x-ssl-cert") String clientCert,
      @PathVariable("tenant-id") TenantIdentifier tenantId,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    UserinfoRequestResponse response =
        userinfoApi.request(tenantId, authorizationHeader, clientCert, requestAttributes);

    return new ResponseEntity<>(response.response(), HttpStatus.valueOf(response.statusCode()));
  }
}
