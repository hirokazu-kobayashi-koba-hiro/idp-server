package org.idp.sample;

import java.util.List;
import java.util.Map;
import org.idp.server.IdpServerApplication;
import org.idp.server.OAuthApi;
import org.idp.server.basic.date.SystemDateTime;
import org.idp.server.handler.oauth.io.*;
import org.idp.server.oauth.authentication.Authentication;
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
  UserMockService userMockService;

  public OAuthDebugController(
      IdpServerApplication idpServerApplication, UserMockService userMockService) {
    this.oAuthApi = idpServerApplication.oAuthApi();
    oAuthApi.setOAuthRequestDelegate(userMockService);
    this.userMockService = userMockService;
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
      case NO_INTERACTION_OK, REDIRECABLE_BAD_REQUEST -> {
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
    User user = userMockService.getUser();
    Authentication authentication =
        new Authentication()
            .setTime(SystemDateTime.now())
            .setMethods(List.of("password"))
            .setAcrValues(List.of("urn:mace:incommon:iap:silver"));

    OAuthAuthorizeRequest authAuthorizeRequest =
        new OAuthAuthorizeRequest(id, tenant.issuer(), user).setAuthentication(authentication);
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
