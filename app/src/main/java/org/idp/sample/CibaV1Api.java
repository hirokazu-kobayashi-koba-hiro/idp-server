package org.idp.sample;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.idp.server.CibaApi;
import org.idp.server.IdpServerApplication;
import org.idp.server.ciba.CibaRequestDelegate;
import org.idp.server.ciba.UserCriteria;
import org.idp.server.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.handler.ciba.io.CibaAuthorizeRequest;
import org.idp.server.handler.ciba.io.CibaAuthorizeResponse;
import org.idp.server.handler.ciba.io.CibaRequest;
import org.idp.server.handler.ciba.io.CibaRequestResponse;
import org.idp.server.oauth.identity.User;
import org.idp.server.type.ciba.UserCode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("{tenant-id}/api/v1/backchannel/authentications")
public class CibaV1Api implements ParameterTransformable {

  CibaApi cibaApi;

  public CibaV1Api(IdpServerApplication idpServerApplication) {
    this.cibaApi = idpServerApplication.cibaApi();
  }

  @PostMapping
  public ResponseEntity<?> request(
      @RequestBody(required = false) MultiValueMap<String, String> body,
      @RequestHeader(required = false, value = "Authorization") String authorizationHeader,
      @PathVariable("tenant-id") String tenantId) {
    Map<String, String[]> params = transform(body);
    Tenant tenant = Tenant.of(tenantId);
    CibaRequest cibaRequest = new CibaRequest(authorizationHeader, params, tenant.issuer());
    CibaRequestResponse response =
        cibaApi.request(
            cibaRequest,
            new CibaRequestDelegate() {

              @Override
              public User find(UserCriteria criteria) {
                // TODO official implementation
                if (!"001".equals(criteria.loginHint().value())) {
                  return new User();
                }
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

              @Override
              public boolean authenticate(User user, UserCode userCode) {
                // TODO official implementation
                return "successUserCode".equals(userCode.value());
              }

              @Override
              public void notify(User user, BackchannelAuthenticationRequest request) {
                // TODO official implementation
              }
            });
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
    return new ResponseEntity<>(
        response.response(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @PostMapping("/automated-complete")
  public ResponseEntity<?> complete(
      @RequestParam("auth_req_id") String authReqId,
      @RequestParam("action") String action,
      @PathVariable("tenant-id") String tenantId) {
    Tenant tenant = Tenant.of(tenantId);
    CibaAuthorizeRequest cibaAuthorizeRequest =
        new CibaAuthorizeRequest(authReqId, tenant.issuer());
    if (action.equals("allow")) {
      CibaAuthorizeResponse authorizeResponse = cibaApi.authorize(cibaAuthorizeRequest);
      return new ResponseEntity<>(HttpStatus.valueOf(authorizeResponse.statusCode()));
    }
    return new ResponseEntity<>(HttpStatus.OK);
  }
}
