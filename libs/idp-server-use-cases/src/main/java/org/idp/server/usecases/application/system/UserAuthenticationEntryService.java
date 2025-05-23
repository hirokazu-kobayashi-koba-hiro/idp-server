/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
