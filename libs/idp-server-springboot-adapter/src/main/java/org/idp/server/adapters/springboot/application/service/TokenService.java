package org.idp.server.adapters.springboot.application.service;

import java.util.Map;
import org.idp.server.adapters.springboot.application.service.tenant.TenantService;
import org.idp.server.adapters.springboot.application.service.user.internal.UserService;
import org.idp.server.core.IdpServerApplication;
import org.idp.server.core.api.TokenApi;
import org.idp.server.core.api.TokenIntrospectionApi;
import org.idp.server.core.api.TokenRevocationApi;
import org.idp.server.core.handler.token.io.TokenRequest;
import org.idp.server.core.handler.token.io.TokenRequestResponse;
import org.idp.server.core.handler.tokenintrospection.io.TokenIntrospectionRequest;
import org.idp.server.core.handler.tokenintrospection.io.TokenIntrospectionResponse;
import org.idp.server.core.handler.tokenrevocation.io.TokenRevocationRequest;
import org.idp.server.core.handler.tokenrevocation.io.TokenRevocationResponse;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.token.PasswordCredentialsGrantDelegate;
import org.idp.server.core.type.oauth.Password;
import org.idp.server.core.type.oauth.TokenIssuer;
import org.idp.server.core.type.oauth.Username;
import org.idp.server.adapters.springboot.domain.model.tenant.Tenant;
import org.idp.server.adapters.springboot.domain.model.tenant.TenantIdentifier;
import org.springframework.stereotype.Service;

@Service
public class TokenService implements PasswordCredentialsGrantDelegate {

  TokenApi tokenApi;
  TokenIntrospectionApi tokenIntrospectionApi;
  TokenRevocationApi tokenRevocationApi;
  TenantService tenantService;
  UserService userService;

  public TokenService(
      IdpServerApplication idpServerApplication,
      TenantService tenantService,
      UserService userService) {
    this.tokenApi = idpServerApplication.tokenApi();
    tokenApi.setPasswordCredentialsGrantDelegate(this);
    this.tokenIntrospectionApi = idpServerApplication.tokenIntrospectionApi();
    this.tokenRevocationApi = idpServerApplication.tokenRevocationApi();
    this.tenantService = tenantService;
    this.userService = userService;
  }

  public TokenRequestResponse request(
      TenantIdentifier tenantId,
      Map<String, String[]> params,
      String authorizationHeader,
      String clientCert) {

    Tenant tenant = tenantService.get(tenantId);
    TokenRequest tokenRequest = new TokenRequest(authorizationHeader, params, tenant.issuer());
    tokenRequest.setClientCert(clientCert);

    return tokenApi.request(tokenRequest);
  }

  public TokenIntrospectionResponse inspect(
      TenantIdentifier tenantIdentifier, Map<String, String[]> params) {

    Tenant tenant = tenantService.get(tenantIdentifier);
    TokenIntrospectionRequest tokenIntrospectionRequest =
        new TokenIntrospectionRequest(params, tenant.issuer());

    return tokenIntrospectionApi.inspect(tokenIntrospectionRequest);
  }

  public TokenRevocationResponse revoke(
      TenantIdentifier tenantId,
      Map<String, String[]> request,
      String authorizationHeader,
      String clientCert) {

    Tenant tenant = tenantService.get(tenantId);
    TokenRevocationRequest revocationRequest =
        new TokenRevocationRequest(authorizationHeader, request, tenant.issuer());
    revocationRequest.setClientCert(clientCert);

    return tokenRevocationApi.revoke(revocationRequest);
  }

  // FIXME this is bad code
  @Override
  public User findAndAuthenticate(TokenIssuer tokenIssuer, Username username, Password password) {
    Tenant tenant = tenantService.find(tokenIssuer);
    if (!tenant.exists()) {
      return User.notFound();
    }
    User user = userService.findBy(tenant, username.value(), "idp-server");
    if (!user.exists()) {
      return User.notFound();
    }
    if (!userService.authenticate(user, password.value())) {
      return User.notFound();
    }
    return user;
  }
}
