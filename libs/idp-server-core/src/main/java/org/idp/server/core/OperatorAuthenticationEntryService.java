package org.idp.server.core;

import org.idp.server.core.admin.TokenIntrospectionCreator;
import org.idp.server.core.api.OperatorAuthenticationApi;
import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.handler.tokenintrospection.io.TokenIntrospectionRequest;
import org.idp.server.core.handler.tokenintrospection.io.TokenIntrospectionResponse;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.identity.UserRepository;
import org.idp.server.core.protocol.TokenIntrospectionApi;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantRepository;
import org.idp.server.core.type.exception.UnauthorizedException;
import org.idp.server.core.type.extension.Pairs;

@Transactional
public class OperatorAuthenticationEntryService implements OperatorAuthenticationApi {

  TokenIntrospectionApi tokenIntrospectionApi;
  TenantRepository tenantRepository;
  UserRepository userRepository;

  public OperatorAuthenticationEntryService(
      TokenIntrospectionApi tokenIntrospectionApi,
      TenantRepository tenantRepository,
      UserRepository userRepository) {
    this.tokenIntrospectionApi = tokenIntrospectionApi;
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
        tokenIntrospectionApi.inspect(tokenIntrospectionRequest);

    if (!introspectionResponse.isActive()) {
      throw new UnauthorizedException("error=invalid_token error_description=token is undefined");
    }
    User user = userRepository.get(introspectionResponse.subject());

    return new Pairs<>(user, tokenIntrospectionRequest.token());
  }
}
