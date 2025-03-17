package org.idp.server.core;

import java.util.Map;
import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.api.TokenApi;
import org.idp.server.core.handler.token.io.TokenRequest;
import org.idp.server.core.handler.token.io.TokenRequestResponse;
import org.idp.server.core.handler.tokenintrospection.io.TokenIntrospectionRequest;
import org.idp.server.core.handler.tokenintrospection.io.TokenIntrospectionResponse;
import org.idp.server.core.handler.tokenrevocation.io.TokenRevocationRequest;
import org.idp.server.core.handler.tokenrevocation.io.TokenRevocationResponse;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.protocol.TokenProtocol;
import org.idp.server.core.protocol.TokenIntrospectionApi;
import org.idp.server.core.protocol.TokenRevocationProtocol;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.tenant.TenantService;
import org.idp.server.core.token.PasswordCredentialsGrantDelegate;
import org.idp.server.core.type.oauth.Password;
import org.idp.server.core.type.oauth.TokenIssuer;
import org.idp.server.core.type.oauth.Username;
import org.idp.server.core.user.UserService;

@Transactional
public class TokenEntryService implements TokenApi, PasswordCredentialsGrantDelegate {

  TokenProtocol tokenProtocol;
  TokenIntrospectionApi tokenIntrospectionApi;
  TokenRevocationProtocol tokenRevocationProtocol;
  TenantService tenantService;
  UserService userService;

  public TokenEntryService(
      TokenProtocol tokenProtocol,
      TokenIntrospectionApi tokenIntrospectionApi,
      TokenRevocationProtocol tokenRevocationProtocol,
      UserService userService,
      TenantService tenantService) {
    this.tokenProtocol = tokenProtocol;
    tokenProtocol.setPasswordCredentialsGrantDelegate(this);
    this.tokenIntrospectionApi = tokenIntrospectionApi;
    this.tokenRevocationProtocol = tokenRevocationProtocol;
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

    return tokenProtocol.request(tokenRequest);
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

    return tokenRevocationProtocol.revoke(revocationRequest);
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
