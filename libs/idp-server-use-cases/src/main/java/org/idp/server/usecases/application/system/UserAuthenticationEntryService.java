package org.idp.server.usecases.application.system;

import org.idp.server.basic.type.extension.Pairs;
import org.idp.server.control_plane.base.TokenIntrospectionCreator;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.UserAuthenticationApi;
import org.idp.server.core.oidc.identity.UserIdentifier;
import org.idp.server.core.oidc.identity.repository.UserQueryRepository;
import org.idp.server.core.oidc.token.OAuthToken;
import org.idp.server.core.oidc.token.TokenProtocol;
import org.idp.server.core.oidc.token.TokenProtocols;
import org.idp.server.core.oidc.token.handler.tokenintrospection.io.TokenIntrospectionRequest;
import org.idp.server.core.oidc.token.handler.tokenintrospection.io.TokenIntrospectionResponse;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.exception.UnauthorizedException;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;

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

    if (introspectionResponse.isClientCredentialsGrant()) {
      return Pairs.of(User.notFound(), introspectionResponse.oAuthToken());
    }

    UserIdentifier userIdentifier = new UserIdentifier(introspectionResponse.subject());
    User user = userQueryRepository.get(adminTenant, userIdentifier);

    return new Pairs<>(user, introspectionResponse.oAuthToken());
  }
}
