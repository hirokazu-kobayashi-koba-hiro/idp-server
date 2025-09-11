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

import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserAuthenticationApi;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.core.openid.token.TokenProtocol;
import org.idp.server.core.openid.token.TokenProtocols;
import org.idp.server.core.openid.token.handler.tokenintrospection.io.TokenIntrospectionInternalRequest;
import org.idp.server.core.openid.token.handler.tokenintrospection.io.TokenIntrospectionResponse;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.exception.UnauthorizedException;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.Pairs;

@Transaction
public class UserAuthenticationEntryService implements UserAuthenticationApi {

  TokenProtocols tokenProtocols;
  TenantQueryRepository tenantQueryRepository;
  UserQueryRepository userQueryRepository;
  OrganizationRepository organizationRepository;

  public UserAuthenticationEntryService(
      TokenProtocols tokenProtocols,
      TenantQueryRepository tenantQueryRepository,
      UserQueryRepository userQueryRepository,
      OrganizationRepository organizationRepository) {
    this.tokenProtocols = tokenProtocols;
    this.tenantQueryRepository = tenantQueryRepository;
    this.userQueryRepository = userQueryRepository;
    this.organizationRepository = organizationRepository;
  }

  public Pairs<User, OAuthToken> authenticate(
      TenantIdentifier tenantIdentifier, String authorizationHeader, String clientCert) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    TokenIntrospectionInternalRequest tokenIntrospectionInternalRequest =
        new TokenIntrospectionInternalRequest(tenant, authorizationHeader, clientCert);

    TokenProtocol tokenProtocol = tokenProtocols.get(tenant.authorizationProvider());

    TokenIntrospectionResponse introspectionResponse =
        tokenProtocol.inspectForInternal(tokenIntrospectionInternalRequest);

    if (!introspectionResponse.isActive()) {
      throw new UnauthorizedException("error=invalid_token error_description=token is not active");
    }

    if (introspectionResponse.isClientCredentialsGrant()) {
      return Pairs.of(User.notFound(), introspectionResponse.oAuthToken());
    }

    UserIdentifier userIdentifier = new UserIdentifier(introspectionResponse.subject());
    User user = userQueryRepository.get(tenant, userIdentifier);

    return new Pairs<>(user, introspectionResponse.oAuthToken());
  }
}
