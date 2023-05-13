package org.idp.sample;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.idp.server.IdpServerApplication;
import org.idp.server.OAuthApi;
import org.idp.server.handler.oauth.io.*;
import org.idp.server.oauth.identity.User;
import org.idp.server.type.extension.OAuthDenyReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("{tenant-id}/api/debug/v1/authorizations")
public class OAuthDebugController implements ParameterTransformable {

  Logger log = LoggerFactory.getLogger(OAuthDebugController.class);

  OAuthApi oAuthApi;

  public OAuthDebugController(IdpServerApplication idpServerApplication) {
    this.oAuthApi = idpServerApplication.oAuthApi();
  }

  @GetMapping
  public ResponseEntity<?> get(
      @RequestParam(required = false) MultiValueMap<String, String> request,
      @PathVariable("tenant-id") String tenantId) {
    Map<String, String[]> params = transform(request);
    Tenant tenant = Tenant.of(tenantId);
    OAuthRequest oAuthRequest = new OAuthRequest(params, tenant.issuer());
    OAuthRequestResponse response = oAuthApi.request(oAuthRequest);
    switch (response.status()) {
      case OK -> {
        return new ResponseEntity<>(response.contents(), HttpStatus.OK);
      }
      case REDIRECABLE_BAD_REQUEST -> {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("location", response.redirectUri());
        return new ResponseEntity<>(httpHeaders, HttpStatus.FOUND);
      }
      case BAD_REQUEST -> {
        return new ResponseEntity<>(response.contents(), HttpStatus.BAD_REQUEST);
      }
      default -> {
        return new ResponseEntity<>(response.contents(), HttpStatus.INTERNAL_SERVER_ERROR);
      }
    }
  }

  @PostMapping("/{id}/authorize")
  public ResponseEntity<?> authorize(
      @PathVariable String id, @PathVariable("tenant-id") String tenantId) {
    Tenant tenant = Tenant.of(tenantId);
    User user =
        new User()
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
    OAuthAuthorizeRequest authAuthorizeRequest =
        new OAuthAuthorizeRequest(id, tenant.issuer(), user);
    OAuthAuthorizeResponse authAuthorizeResponse = oAuthApi.authorize(authAuthorizeRequest);
    Map<String, String> response = Map.of("redirect_uri", authAuthorizeResponse.redirectUriValue());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @PostMapping("/{id}/deny")
  public ResponseEntity<?> deny(
      @PathVariable String id, @PathVariable("tenant-id") String tenantId) {
    Tenant tenant = Tenant.of(tenantId);
    OAuthDenyRequest denyRequest =
        new OAuthDenyRequest(id, tenant.issuer(), OAuthDenyReason.access_denied);
    OAuthDenyResponse oAuthDenyResponse = oAuthApi.deny(denyRequest);
    Map<String, String> response = Map.of("redirect_uri", oAuthDenyResponse.redirectUriValue());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }
}
