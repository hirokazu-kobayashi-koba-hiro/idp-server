package org.idp.sample;

import java.util.Map;

import org.idp.sample.user.UserService;
import org.idp.server.IdpServerApplication;
import org.idp.server.TokenApi;
import org.idp.server.handler.token.io.TokenRequest;
import org.idp.server.handler.token.io.TokenRequestResponse;
import org.idp.server.oauth.identity.User;
import org.idp.server.token.PasswordCredentialsGrantDelegate;
import org.idp.server.type.oauth.Password;
import org.idp.server.type.oauth.TokenIssuer;
import org.idp.server.type.oauth.Username;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("{tenant-id}/api/v1/tokens")
public class TokenV1Api implements PasswordCredentialsGrantDelegate, ParameterTransformable {

  TokenApi tokenApi;
  UserService userService;

  public TokenV1Api(
      IdpServerApplication idpServerApplication, UserService userService) {
    this.tokenApi = idpServerApplication.tokenApi();
    tokenApi.setPasswordCredentialsGrantDelegate(this);
    this.userService = userService;
  }

  @PostMapping
  public ResponseEntity<?> request(
      @RequestBody(required = false) MultiValueMap<String, String> body,
      @RequestHeader(required = false, value = "Authorization") String authorizationHeader,
      @RequestHeader(required = false, value = "x-ssl-cert") String clientCert,
      @PathVariable("tenant-id") String tenantId) {
    Map<String, String[]> request = transform(body);
    Tenant tenant = Tenant.of(tenantId);
    TokenRequest tokenRequest = new TokenRequest(authorizationHeader, request, tenant.issuer());
    tokenRequest.setClientCert(clientCert);
    TokenRequestResponse response = tokenApi.request(tokenRequest);
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.setAll(response.responseHeaders());
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @Override
  public User findAndAuthenticate(TokenIssuer tokenIssuer, Username username, Password password) {
    User user = userService.find(username.value());
    if (!user.exists()) {
      return User.notFound();
    }
    if (!userService.authenticate(user, password.value())) {
      return User.notFound();
    }
    return user;
  }
}
