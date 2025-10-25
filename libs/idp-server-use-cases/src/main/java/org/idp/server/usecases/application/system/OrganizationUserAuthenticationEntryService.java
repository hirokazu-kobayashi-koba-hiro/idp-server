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

import org.idp.server.control_plane.base.OrganizationAuthenticationContext;
import org.idp.server.control_plane.base.OrganizationUserAuthenticationApi;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.token.TokenProtocol;
import org.idp.server.core.openid.token.TokenProtocols;
import org.idp.server.core.openid.token.handler.tokenintrospection.io.TokenIntrospectionInternalRequest;
import org.idp.server.core.openid.token.handler.tokenintrospection.io.TokenIntrospectionResponse;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.exception.UnauthorizedException;
import org.idp.server.platform.multi_tenancy.organization.AssignedTenant;
import org.idp.server.platform.multi_tenancy.organization.Organization;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.Pairs;

@Transaction
public class OrganizationUserAuthenticationEntryService
    implements OrganizationUserAuthenticationApi {

  TokenProtocols tokenProtocols;
  TenantQueryRepository tenantQueryRepository;
  UserQueryRepository userQueryRepository;
  OrganizationRepository organizationRepository;

  public OrganizationUserAuthenticationEntryService(
      TokenProtocols tokenProtocols,
      TenantQueryRepository tenantQueryRepository,
      UserQueryRepository userQueryRepository,
      OrganizationRepository organizationRepository) {
    this.tokenProtocols = tokenProtocols;
    this.tenantQueryRepository = tenantQueryRepository;
    this.userQueryRepository = userQueryRepository;
    this.organizationRepository = organizationRepository;
  }

  /**
   * Authenticates a user for organization-level operations.
   *
   * <p>This method is designed to work with OrganizationAwareEntryServiceProxy, which automatically
   * resolves the admin tenant ID from the organization and handles the tenant resolution
   * transparently.
   *
   * @param organizationId the organization identifier (proxy will resolve admin tenant)
   * @param authorizationHeader the Authorization header value
   * @param clientCert the client certificate (optional)
   * @return a pair of authenticated User and OAuthToken
   * @throws UnauthorizedException if authentication fails
   */
  public Pairs<User, OrganizationAuthenticationContext> authenticate(
      OrganizationIdentifier organizationId, String authorizationHeader, String clientCert) {

    Organization organization = organizationRepository.get(organizationId);
    AssignedTenant orgTenant = organization.findOrgTenant();
    TenantIdentifier orgTenantId = orgTenant.tenantIdentifier();
    Tenant tenant = tenantQueryRepository.get(orgTenantId);
    TokenIntrospectionInternalRequest tokenIntrospectionInternalRequest =
        new TokenIntrospectionInternalRequest(tenant, authorizationHeader, clientCert);

    TokenProtocol tokenProtocol = tokenProtocols.get(tenant.authorizationProvider());

    TokenIntrospectionResponse introspectionResponse =
        tokenProtocol.inspectForInternal(tokenIntrospectionInternalRequest);

    if (!introspectionResponse.isActive()) {
      throw new UnauthorizedException("error=invalid_token error_description=token is not active");
    }

    if (introspectionResponse.isClientCredentialsGrant()) {
      throw new UnauthorizedException(
          "error=invalid_token error_description=client_credentials_grant is forbidden management api.");
    }

    UserIdentifier userIdentifier = new UserIdentifier(introspectionResponse.subject());
    User user = userQueryRepository.get(tenant, userIdentifier);

    OrganizationAuthenticationContext authenticationContext =
        new OrganizationAuthenticationContext(
            organization, tenant, introspectionResponse.oAuthToken(), user);

    return new Pairs<>(user, authenticationContext);
  }
}
