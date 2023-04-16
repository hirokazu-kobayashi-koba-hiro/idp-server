package org.idp.sample;

import java.util.Map;
import org.idp.server.IdpServerApplication;
import org.idp.server.OAuthApi;
import org.idp.server.handler.io.OAuthAuthorizeRequest;
import org.idp.server.handler.io.OAuthAuthorizeResponse;
import org.idp.server.handler.io.OAuthRequest;
import org.idp.server.handler.io.OAuthRequestResponse;
import org.idp.server.oauth.identity.User;
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
    User user = new User().setSub("001");
    OAuthAuthorizeRequest authAuthorizeRequest =
        new OAuthAuthorizeRequest(id, tenant.issuer(), user);
    OAuthAuthorizeResponse authAuthorizeResponse = oAuthApi.authorize(authAuthorizeRequest);
    Map<String, String> response = Map.of("redirect_uri", authAuthorizeResponse.redirectUriValue());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }
}
