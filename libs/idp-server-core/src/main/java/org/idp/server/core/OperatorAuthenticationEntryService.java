package org.idp.server.core;

import org.idp.server.core.admin.OperatorAuthenticationApi;
import org.idp.server.core.admin.TokenIntrospectionCreator;
import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.identity.UserRepository;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantRepository;
import org.idp.server.core.token.TokenIntrospectionProtocol;
import org.idp.server.core.token.handler.tokenintrospection.io.TokenIntrospectionRequest;
import org.idp.server.core.token.handler.tokenintrospection.io.TokenIntrospectionResponse;
import org.idp.server.core.type.exception.UnauthorizedException;
import org.idp.server.core.type.extension.Pairs;

@Transactional
public class OperatorAuthenticationEntryService implements OperatorAuthenticationApi {

  TokenIntrospectionProtocol tokenIntrospectionProtocol;
  TenantRepository tenantRepository;
  UserRepository userRepository;

  public OperatorAuthenticationEntryService(
      TokenIntrospectionProtocol tokenIntrospectionProtocol,
      TenantRepository tenantRepository,
      UserRepository userRepository) {
    this.tokenIntrospectionProtocol = tokenIntrospectionProtocol;
    this.tenantRepository = tenantRepository;
    this.userRepository = userRepository;
  }

  public Pairs<User, String> authenticate(String authorizationHeader) {
    Tenant adminTenant = tenantRepository.getAdmin();

    TokenIntrospectionCreator tokenIntrospectionCreator =
        new TokenIntrospectionCreator(adminTenant, authorizationHeader);
    TokenIntrospectionRequest tokenIntrospectionRequest = tokenIntrospectionCreator.create();

    if (!tokenIntrospectionRequest.hasToken()) {
      throw new UnauthorizedException("error=invalid_token error_description=token is undefined");
    }

    TokenIntrospectionResponse introspectionResponse =
        tokenIntrospectionProtocol.inspect(tokenIntrospectionRequest);

    if (!introspectionResponse.isActive()) {
      throw new UnauthorizedException("error=invalid_token error_description=token is undefined");
    }
    User user = userRepository.get(introspectionResponse.subject());

    return new Pairs<>(user, tokenIntrospectionRequest.token());
  }
}
