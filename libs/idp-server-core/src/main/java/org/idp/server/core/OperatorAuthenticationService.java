package org.idp.server.core;

import org.idp.server.core.admin.TokenIntrospectionCreator;
import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.function.OperatorAuthenticationFunction;
import org.idp.server.core.handler.tokenintrospection.io.TokenIntrospectionRequest;
import org.idp.server.core.handler.tokenintrospection.io.TokenIntrospectionResponse;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.protcol.TokenIntrospectionApi;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantService;
import org.idp.server.core.type.exception.UnauthorizedException;
import org.idp.server.core.type.extension.Pairs;
import org.idp.server.core.user.UserService;

@Transactional
public class OperatorAuthenticationService implements OperatorAuthenticationFunction {

  TokenIntrospectionApi tokenIntrospectionApi;
  TenantService tenantService;
  UserService userService;

  public OperatorAuthenticationService(
      TokenIntrospectionApi tokenIntrospectionApi,
      TenantService tenantService,
      UserService userService) {
    this.tokenIntrospectionApi = tokenIntrospectionApi;
    this.tenantService = tenantService;
    this.userService = userService;
  }

  public Pairs<User, String> authenticate(String authorizationHeader) {
    Tenant adminTenant = tenantService.getAdmin();

    TokenIntrospectionCreator tokenIntrospectionCreator =
        new TokenIntrospectionCreator(authorizationHeader, adminTenant.issuer());
    TokenIntrospectionRequest tokenIntrospectionRequest = tokenIntrospectionCreator.create();

    if (!tokenIntrospectionRequest.hasToken()) {
      throw new UnauthorizedException("error=invalid_token error_description=token is undefined");
    }

    TokenIntrospectionResponse introspectionResponse =
        tokenIntrospectionApi.inspect(tokenIntrospectionRequest);

    if (!introspectionResponse.isActive()) {
      throw new UnauthorizedException("error=invalid_token error_description=token is undefined");
    }
    User user = userService.get(introspectionResponse.subject());

    return new Pairs<>(user, tokenIntrospectionRequest.token());
  }
}
