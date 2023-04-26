package org.idp.sample;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.idp.server.IdpServerApplication;
import org.idp.server.UserinfoApi;
import org.idp.server.handler.userinfo.UserinfoDelegate;
import org.idp.server.handler.userinfo.io.UserinfoRequest;
import org.idp.server.handler.userinfo.io.UserinfoRequestResponse;
import org.idp.server.oauth.identity.User;
import org.idp.server.type.oauth.Subject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("{tenant-id}/api/v1/userinfo")
public class UserinfoV1Api implements ParameterTransformable, UserinfoDelegate {

  UserinfoApi userinfoApi;

  public UserinfoV1Api(IdpServerApplication idpServerApplication) {
    this.userinfoApi = idpServerApplication.userinfoApi();
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
    return new User()
        .setSub("001")
        .setName("ito ichiro")
        .setGivenName("ichiro")
        .setFamilyName("ito")
        .setNickname("ito")
        .setPreferredUsername("ichiro")
        .setProfile("https://example.com/profiles/123")
        .setPicture("https://example.com/pictures/123")
        .setWebsite("https://example.com")
        .setEmail("ito.ichiro@gmail.com")
        .setEmailVerified(true)
        .setGender("other")
        .setBirthdate("2000-02-02")
        .setZoneinfo("ja-jp")
        .setLocale("locale")
        .setPhoneNumber("09012345678")
        .setPhoneNumberVerified(false)
        .setUpdateAt(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC));
  }
}
