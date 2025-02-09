package org.idp.sample.presentation.api;

import java.util.Map;
import org.idp.sample.application.service.TenantService;
import org.idp.sample.application.service.user.UserService;
import org.idp.sample.domain.model.tenant.Tenant;
import org.idp.sample.domain.model.tenant.TenantIdentifier;
import org.idp.server.IdpServerApplication;
import org.idp.server.api.TokenApi;
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
  TenantService tenantService;

  public TokenV1Api(
      IdpServerApplication idpServerApplication,
      UserService userService,
      TenantService tenantService) {
    this.tokenApi = idpServerApplication.tokenApi();
    tokenApi.setPasswordCredentialsGrantDelegate(this);
    this.userService = userService;
    this.tenantService = tenantService;
  }

  @PostMapping
  public ResponseEntity<?> request(
      @RequestBody(required = false) MultiValueMap<String, String> body,
      @RequestHeader(required = false, value = "Authorization") String authorizationHeader,
      @RequestHeader(required = false, value = "x-ssl-cert") String clientCert,
      @PathVariable("tenant-id") TenantIdentifier tenantId) {
    Map<String, String[]> request = transform(body);
    Tenant tenant = tenantService.get(tenantId);
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
