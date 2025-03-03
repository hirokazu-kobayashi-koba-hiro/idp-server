package org.idp.server.presentation.api;

import org.idp.server.application.service.UserinfoService;
import org.idp.server.core.handler.userinfo.io.UserinfoRequestResponse;
import org.idp.server.domain.model.tenant.TenantIdentifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("{tenant-id}/api/v1/userinfo")
public class UserinfoV1Api implements ParameterTransformable {

  UserinfoService userinfoService;

  public UserinfoV1Api(UserinfoService userinfoService) {
    this.userinfoService = userinfoService;
  }

  @GetMapping
  public ResponseEntity<?> get(
      @RequestHeader(required = false, value = "Authorization") String authorizationHeader,
      @RequestHeader(required = false, value = "x-ssl-cert") String clientCert,
      @PathVariable("tenant-id") TenantIdentifier tenantId) {

    UserinfoRequestResponse response =
        userinfoService.request(tenantId, authorizationHeader, clientCert);

    return new ResponseEntity<>(response.response(), HttpStatus.valueOf(response.statusCode()));
  }

  @PostMapping
  public ResponseEntity<?> post(
      @RequestHeader(required = false, value = "Authorization") String authorizationHeader,
      @RequestHeader(required = false, value = "x-ssl-cert") String clientCert,
      @PathVariable("tenant-id") TenantIdentifier tenantId) {

    UserinfoRequestResponse response =
        userinfoService.request(tenantId, authorizationHeader, clientCert);

    return new ResponseEntity<>(response.response(), HttpStatus.valueOf(response.statusCode()));
  }
}
