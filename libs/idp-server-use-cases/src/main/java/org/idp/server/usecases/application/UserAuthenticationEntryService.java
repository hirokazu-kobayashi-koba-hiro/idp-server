package org.idp.server.usecases.application;

import org.idp.server.basic.datasource.Transaction;
import org.idp.server.basic.exception.UnauthorizedException;
import org.idp.server.basic.type.extension.Pairs;
import org.idp.server.control.plane.TokenIntrospectionCreator;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserAuthenticationApi;
import org.idp.server.core.identity.UserIdentifier;
import org.idp.server.core.identity.repository.UserQueryRepository;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.core.token.TokenProtocol;
import org.idp.server.core.token.TokenProtocols;
import org.idp.server.core.token.handler.tokenintrospection.io.TokenIntrospectionRequest;
import org.idp.server.core.token.handler.tokenintrospection.io.TokenIntrospectionResponse;

@Transaction
public class UserAuthenticationEntryService implements UserAuthenticationApi {

  TokenProtocols tokenProtocols;
  TenantQueryRepository tenantQueryRepository;
  UserQueryRepository userQueryRepository;

  public UserAuthenticationEntryService(
      TokenProtocols tokenProtocols,
      TenantQueryRepository tenantQueryRepository,
      UserQueryRepository userQueryRepository) {
    this.tokenProtocols = tokenProtocols;
    this.tenantQueryRepository = tenantQueryRepository;
    this.userQueryRepository = userQueryRepository;
  }

  public Pairs<User, OAuthToken> authenticate(
      TenantIdentifier tenantIdentifier, String authorizationHeader) {
    Tenant adminTenant = tenantQueryRepository.get(tenantIdentifier);

    TokenIntrospectionCreator tokenIntrospectionCreator =
        new TokenIntrospectionCreator(adminTenant, authorizationHeader);
    TokenIntrospectionRequest tokenIntrospectionRequest = tokenIntrospectionCreator.create();

    if (!tokenIntrospectionRequest.hasToken()) {
      throw new UnauthorizedException("error=invalid_token error_description=token is undefined");
    }

    TokenProtocol tokenProtocol = tokenProtocols.get(adminTenant.authorizationProvider());

    TokenIntrospectionResponse introspectionResponse =
        tokenProtocol.inspect(tokenIntrospectionRequest);

    if (!introspectionResponse.isActive()) {
      throw new UnauthorizedException("error=invalid_token error_description=token is undefined");
    }
    UserIdentifier userIdentifier = new UserIdentifier(introspectionResponse.subject());
    User user = userQueryRepository.get(adminTenant, userIdentifier);

    return new Pairs<>(user, introspectionResponse.oAuthToken());
  }
}
