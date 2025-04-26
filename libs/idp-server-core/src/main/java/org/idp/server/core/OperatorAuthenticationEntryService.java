package org.idp.server.core;

import org.idp.server.core.admin.OperatorAuthenticationApi;
import org.idp.server.core.admin.TokenIntrospectionCreator;
import org.idp.server.core.basic.datasource.Transaction;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserRepository;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.tenant.TenantRepository;
import org.idp.server.core.token.TokenProtocol;
import org.idp.server.core.token.TokenProtocols;
import org.idp.server.core.token.handler.tokenintrospection.io.TokenIntrospectionRequest;
import org.idp.server.core.token.handler.tokenintrospection.io.TokenIntrospectionResponse;
import org.idp.server.core.type.exception.UnauthorizedException;
import org.idp.server.core.type.extension.Pairs;

@Transaction
public class OperatorAuthenticationEntryService implements OperatorAuthenticationApi {

  TokenProtocols tokenProtocols;
  TenantRepository tenantRepository;
  UserRepository userRepository;

  public OperatorAuthenticationEntryService(
      TokenProtocols tokenProtocols,
      TenantRepository tenantRepository,
      UserRepository userRepository) {
    this.tokenProtocols = tokenProtocols;
    this.tenantRepository = tenantRepository;
    this.userRepository = userRepository;
  }

  public Pairs<User, String> authenticate(
      TenantIdentifier adminTenantIdentifier, String authorizationHeader) {
    Tenant adminTenant = tenantRepository.getAdmin();

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
    User user = userRepository.get(adminTenant, introspectionResponse.subject());

    return new Pairs<>(user, tokenIntrospectionRequest.token());
  }
}
