package org.idp.server.usecases;

import org.idp.server.basic.datasource.Transaction;
import org.idp.server.basic.exception.UnauthorizedException;
import org.idp.server.basic.type.extension.Pairs;
import org.idp.server.core.admin.OperatorAuthenticationApi;
import org.idp.server.core.admin.TokenIntrospectionCreator;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserIdentifier;
import org.idp.server.core.identity.repository.UserQueryRepository;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantRepository;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.core.token.TokenProtocol;
import org.idp.server.core.token.TokenProtocols;
import org.idp.server.core.token.handler.tokenintrospection.io.TokenIntrospectionRequest;
import org.idp.server.core.token.handler.tokenintrospection.io.TokenIntrospectionResponse;

@Transaction
public class OperatorAuthenticationEntryService implements OperatorAuthenticationApi {

  TokenProtocols tokenProtocols;
  TenantRepository tenantRepository;
  UserQueryRepository userQueryRepository;

  public OperatorAuthenticationEntryService(
      TokenProtocols tokenProtocols,
      TenantRepository tenantRepository,
      UserQueryRepository userQueryRepository) {
    this.tokenProtocols = tokenProtocols;
    this.tenantRepository = tenantRepository;
    this.userQueryRepository = userQueryRepository;
  }

  public Pairs<User, OAuthToken> authenticate(
      TenantIdentifier tenantIdentifier, String authorizationHeader) {
    Tenant adminTenant = tenantRepository.get(tenantIdentifier);

    TokenIntrospectionCreator tokenIntrospectionCreator =
        new TokenIntrospectionCreator(adminTenant, authorizationHeader);
    TokenIntrospectionRequest tokenIntrospectionRequest = tokenIntrospectionCreator.create();

    if (!tokenIntrospectionRequest.hasToken()) {
      throw new UnauthorizedException("error=invalid_token error_description=token is undefined");
    }

    TokenProtocol tokenProtocol = tokenProtocols.get(adminTenant.authorizationProtocolProvider());

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
