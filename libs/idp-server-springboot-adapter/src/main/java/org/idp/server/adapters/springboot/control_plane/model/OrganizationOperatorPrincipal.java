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

package org.idp.server.adapters.springboot.control_plane.model;

import java.util.List;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.springframework.security.authentication.AbstractAuthenticationToken;

/**
 * Organization-level operator principal for Spring Security authentication.
 *
 * <p>This class extends the basic OperatorPrincipal to include organization context and admin
 * tenant information for organization-level management operations.
 *
 * <p>Key components: - Organization identifier for multi-organization isolation - Admin tenant
 * identifier for organization management operations - User and OAuth token for authentication -
 * Organization-specific authorities
 */
public class OrganizationOperatorPrincipal extends AbstractAuthenticationToken {

  private final User user;
  private final OAuthToken oAuthToken;
  private final OrganizationIdentifier organizationId;
  private final TenantIdentifier adminTenantId;

  /**
   * Constructs a new OrganizationOperatorPrincipal.
   *
   * @param user the authenticated user
   * @param oAuthToken the OAuth token
   * @param organizationId the organization identifier
   * @param adminTenantId the admin tenant identifier for the organization
   * @param authorities the organization-level authorities
   */
  public OrganizationOperatorPrincipal(
      User user,
      OAuthToken oAuthToken,
      OrganizationIdentifier organizationId,
      TenantIdentifier adminTenantId,
      List<IdpControlPlaneAuthority> authorities) {
    super(authorities);
    this.user = user;
    this.oAuthToken = oAuthToken;
    this.organizationId = organizationId;
    this.adminTenantId = adminTenantId;
  }

  @Override
  public Object getCredentials() {
    return oAuthToken;
  }

  @Override
  public Object getPrincipal() {
    return this;
  }

  /**
   * Gets the authenticated user.
   *
   * @return the user
   */
  public User getUser() {
    return user;
  }

  /**
   * Gets the OAuth token.
   *
   * @return the OAuth token
   */
  public OAuthToken getOAuthToken() {
    return oAuthToken;
  }

  /**
   * Gets the organization identifier.
   *
   * @return the organization identifier
   */
  public OrganizationIdentifier getOrganizationId() {
    return organizationId;
  }

  /**
   * Gets the admin tenant identifier for the organization.
   *
   * @return the admin tenant identifier
   */
  public TenantIdentifier getAdminTenantId() {
    return adminTenantId;
  }

  /**
   * Gets the requested client ID from the OAuth token.
   *
   * @return the requested client ID
   */
  public RequestedClientId getRequestedClientId() {
    return oAuthToken.requestedClientId();
  }

  /**
   * Checks if the token is a client credentials grant.
   *
   * @return true if client credentials grant
   */
  public boolean isClientCredentialsGrant() {
    return oAuthToken.isClientCredentialsGrant();
  }
}
