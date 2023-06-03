package org.idp.sample;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.idp.server.IdpServerApplication;
import org.idp.server.UserinfoApi;
import org.idp.server.basic.date.SystemDateTime;
import org.idp.server.handler.userinfo.UserinfoDelegate;
import org.idp.server.handler.userinfo.io.UserinfoRequest;
import org.idp.server.handler.userinfo.io.UserinfoRequestResponse;
import org.idp.server.oauth.identity.Address;
import org.idp.server.oauth.identity.User;
import org.idp.server.type.oauth.Subject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("{tenant-id}/api/v1/userinfo")
public class UserinfoV1Api implements ParameterTransformable, UserinfoDelegate {

  UserinfoApi userinfoApi;
  UserMockService userMockService;

  public UserinfoV1Api(IdpServerApplication idpServerApplication, UserMockService userMockService) {
    this.userinfoApi = idpServerApplication.userinfoApi();
    this.userMockService = userMockService;
  }

  @GetMapping
  public ResponseEntity<?> get(
      @RequestHeader(required = false, value = "Authorization") String authorizationHeader,
      @PathVariable("tenant-id") String tenantId) {
    Tenant tenant = Tenant.of(tenantId);
    UserinfoRequest userinfoRequest = new UserinfoRequest(authorizationHeader, tenant.issuer());
    UserinfoRequestResponse response = userinfoApi.request(userinfoRequest, this);
    return new ResponseEntity<>(response.response(), HttpStatus.valueOf(response.statusCode()));
  }

  @PostMapping
  public ResponseEntity<?> post(
      @RequestHeader(required = false, value = "Authorization") String authorizationHeader,
      @PathVariable("tenant-id") String tenantId) {
    Tenant tenant = Tenant.of(tenantId);
    UserinfoRequest userinfoRequest = new UserinfoRequest(authorizationHeader, tenant.issuer());
    UserinfoRequestResponse response = userinfoApi.request(userinfoRequest, this);
    return new ResponseEntity<>(response.response(), HttpStatus.valueOf(response.statusCode()));
  }

  @Override
  public User getUser(Subject subject) {
    return userMockService.getUser();
  }
}
