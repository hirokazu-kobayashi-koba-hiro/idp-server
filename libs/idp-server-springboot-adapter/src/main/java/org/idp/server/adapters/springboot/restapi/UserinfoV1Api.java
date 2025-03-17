package org.idp.server.adapters.springboot.restapi;

import org.idp.server.core.adapters.IdpServerApplication;
import org.idp.server.core.api.UserinfoApi;
import org.idp.server.core.handler.userinfo.io.UserinfoRequestResponse;
import org.idp.server.core.tenant.TenantIdentifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("{tenant-id}/api/v1/userinfo")
public class UserinfoV1Api implements ParameterTransformable {

  UserinfoApi userinfoApi;

  public UserinfoV1Api(IdpServerApplication idpServerApplication) {
    this.userinfoApi = idpServerApplication.userinfoFunction();
  }

  @GetMapping
  public ResponseEntity<?> get(
      @RequestHeader(required = false, value = "Authorization") String authorizationHeader,
      @RequestHeader(required = false, value = "x-ssl-cert") String clientCert,
      @PathVariable("tenant-id") TenantIdentifier tenantId) {

    UserinfoRequestResponse response =
        userinfoApi.request(tenantId, authorizationHeader, clientCert);

    return new ResponseEntity<>(response.response(), HttpStatus.valueOf(response.statusCode()));
  }

  @PostMapping
  public ResponseEntity<?> post(
      @RequestHeader(required = false, value = "Authorization") String authorizationHeader,
      @RequestHeader(required = false, value = "x-ssl-cert") String clientCert,
      @PathVariable("tenant-id") TenantIdentifier tenantId) {

    UserinfoRequestResponse response =
        userinfoApi.request(tenantId, authorizationHeader, clientCert);

    return new ResponseEntity<>(response.response(), HttpStatus.valueOf(response.statusCode()));
  }
}
