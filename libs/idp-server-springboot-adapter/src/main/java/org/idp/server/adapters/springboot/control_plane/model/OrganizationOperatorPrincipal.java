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
import org.idp.server.control_plane.base.OrganizationAuthenticationContext;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
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

  private final OrganizationAuthenticationContext authenticationContext;

  /**
   * Constructs a new OrganizationOperatorPrincipal.
   *
   * @param authenticationContext the authentication contextr
   * @param authorities the organization-level authorities
   */
  public OrganizationOperatorPrincipal(
      OrganizationAuthenticationContext authenticationContext,
      List<IdpControlPlaneAuthority> authorities) {
    super(authorities);
    this.authenticationContext = authenticationContext;
  }

  @Override
  public Object getCredentials() {
    return authenticationContext.oAuthToken();
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
    return authenticationContext.operator();
  }

  /**
   * Gets the OAuth token.
   *
   * @return the OAuth token
   */
  public OAuthToken getOAuthToken() {
    return authenticationContext.oAuthToken();
  }

  /**
   * Gets the organization identifier.
   *
   * @return the organization identifier
   */
  public OrganizationIdentifier getOrganizationId() {
    return authenticationContext.organization().identifier();
  }

  public OrganizationAuthenticationContext authenticationContext() {
    return authenticationContext;
  }
}
