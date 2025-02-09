package org.idp.sample.presentation.api;

import org.idp.sample.application.service.TenantService;
import org.idp.sample.application.service.user.UserService;
import org.idp.sample.domain.model.tenant.Tenant;
import org.idp.sample.domain.model.tenant.TenantIdentifier;
import org.idp.server.IdpServerApplication;
import org.idp.server.api.UserinfoApi;
import org.idp.server.handler.userinfo.UserinfoDelegate;
import org.idp.server.handler.userinfo.io.UserinfoRequest;
import org.idp.server.handler.userinfo.io.UserinfoRequestResponse;
import org.idp.server.oauth.identity.User;
import org.idp.server.type.oauth.Subject;
import org.idp.server.type.oauth.TokenIssuer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("{tenant-id}/api/v1/userinfo")
public class UserinfoV1Api implements ParameterTransformable, UserinfoDelegate {

  UserinfoApi userinfoApi;
  UserService userService;
  TenantService tenantService;

  public UserinfoV1Api(
      IdpServerApplication idpServerApplication,
      UserService userService,
      TenantService tenantService) {
    this.userinfoApi = idpServerApplication.userinfoApi();
    this.userService = userService;
    this.tenantService = tenantService;
  }

  @GetMapping
  public ResponseEntity<?> get(
      @RequestHeader(required = false, value = "Authorization") String authorizationHeader,
      @RequestHeader(required = false, value = "x-ssl-cert") String clientCert,
      @PathVariable("tenant-id") TenantIdentifier tenantId) {
    Tenant tenant = tenantService.get(tenantId);
    UserinfoRequest userinfoRequest = new UserinfoRequest(authorizationHeader, tenant.issuer());
    userinfoRequest.setClientCert(clientCert);
    UserinfoRequestResponse response = userinfoApi.request(userinfoRequest, this);
    return new ResponseEntity<>(response.response(), HttpStatus.valueOf(response.statusCode()));
  }

  @PostMapping
  public ResponseEntity<?> post(
      @RequestHeader(required = false, value = "Authorization") String authorizationHeader,
      @RequestHeader(required = false, value = "x-ssl-cert") String clientCert,
      @PathVariable("tenant-id") TenantIdentifier tenantId) {
    Tenant tenant = tenantService.get(tenantId);
    UserinfoRequest userinfoRequest = new UserinfoRequest(authorizationHeader, tenant.issuer());
    userinfoRequest.setClientCert(clientCert);
    UserinfoRequestResponse response = userinfoApi.request(userinfoRequest, this);
    return new ResponseEntity<>(response.response(), HttpStatus.valueOf(response.statusCode()));
  }

  @Override
  public User findUser(TokenIssuer tokenIssuer, Subject subject) {
    return userService.find(subject.value());
  }
}
