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

package org.idp.server.control_plane.management.authentication.interaction;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.control_plane.base.OrganizationAccessVerifier;
import org.idp.server.control_plane.base.OrganizationAuthenticationContext;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.base.definition.DefaultAdminPermission;
import org.idp.server.control_plane.management.authentication.interaction.io.AuthenticationInteractionManagementResponse;
import org.idp.server.core.openid.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.openid.authentication.interaction.AuthenticationInteractionQueries;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Organization-level authentication interaction management API.
 *
 * <p>This interface defines operations for managing authentication interactions within an
 * organization context. It provides read-only operations for authentication interactions with
 * organization-level access control.
 *
 * <p>Organization-level operations follow the standard access control pattern:
 *
 * <ol>
 *   <li><strong>Organization access verification</strong> - Ensures the user has access to the
 *       organization
 *   <li><strong>Permission verification</strong> - Validates the user has necessary
 *       AUTHENTICATION_INTERACTION_READ permissions
 * </ol>
 *
 * <p>This API provides read-only access to authentication interaction data for monitoring and audit
 * purposes within organization boundaries.
 *
 * @see AuthenticationInteractionManagementApi
 * @see OrganizationAccessVerifier
 */
public interface OrgAuthenticationInteractionManagementApi {

  default AdminPermissions getRequiredPermissions(String method) {
    Map<String, AdminPermissions> map = new HashMap<>();
    map.put(
        "findList",
        new AdminPermissions(Set.of(DefaultAdminPermission.AUTHENTICATION_INTERACTION_READ)));
    map.put(
        "get",
        new AdminPermissions(Set.of(DefaultAdminPermission.AUTHENTICATION_INTERACTION_READ)));
    AdminPermissions adminPermissions = map.get(method);
    if (adminPermissions == null) {
      throw new UnSupportedException("Method " + method + " not supported");
    }
    return adminPermissions;
  }

  /**
   * Lists authentication interactions within the organization.
   *
   * @param authenticationContext the organization authentication context
   * @param tenantIdentifier the tenant identifier
   * @param queries the authentication interaction queries
   * @param requestAttributes the request attributes
   * @return the authentication interaction list response
   */
  AuthenticationInteractionManagementResponse findList(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthenticationInteractionQueries queries,
      RequestAttributes requestAttributes);

  /**
   * Gets a specific authentication interaction within the organization.
   *
   * @param authenticationContext the organization authentication context
   * @param tenantIdentifier the tenant identifier
   * @param identifier the authentication transaction identifier
   * @param type the interaction type
   * @param requestAttributes the request attributes
   * @return the authentication interaction details response
   */
  AuthenticationInteractionManagementResponse get(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthenticationTransactionIdentifier identifier,
      String type,
      RequestAttributes requestAttributes);
}
