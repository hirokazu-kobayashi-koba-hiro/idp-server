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

package org.idp.server.control_plane.management.authentication.transaction;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.control_plane.base.OrganizationAccessVerifier;
import org.idp.server.control_plane.base.OrganizationAuthenticationContext;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.base.definition.DefaultAdminPermission;
import org.idp.server.control_plane.management.authentication.transaction.io.AuthenticationTransactionManagementResponse;
import org.idp.server.core.openid.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.openid.authentication.AuthenticationTransactionQueries;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Organization-level authentication transaction management API.
 *
 * <p>This interface defines operations for managing authentication transactions within an
 * organization context. It provides read-only operations for authentication transactions with
 * organization-level access control.
 *
 * <p>Organization-level operations follow the standard access control pattern:
 *
 * <ol>
 *   <li><strong>Organization access verification</strong> - Ensures the user has access to the
 *       organization
 *   <li><strong>Permission verification</strong> - Validates the user has necessary
 *       AUTHENTICATION_TRANSACTION_READ permissions
 * </ol>
 *
 * <p>This API provides read-only access to authentication transaction data for monitoring and audit
 * purposes within organization boundaries.
 *
 * @see AuthenticationTransactionManagementApi
 * @see OrganizationAccessVerifier
 */
public interface OrgAuthenticationTransactionManagementApi {

  default AdminPermissions getRequiredPermissions(String method) {
    Map<String, AdminPermissions> map = new HashMap<>();
    map.put(
        "findList",
        new AdminPermissions(Set.of(DefaultAdminPermission.AUTHENTICATION_TRANSACTION_READ)));
    map.put(
        "get",
        new AdminPermissions(Set.of(DefaultAdminPermission.AUTHENTICATION_TRANSACTION_READ)));
    AdminPermissions adminPermissions = map.get(method);
    if (adminPermissions == null) {
      throw new UnSupportedException("Method " + method + " not supported");
    }
    return adminPermissions;
  }

  /**
   * Lists authentication transactions within the organization.
   *
   * @param authenticationContext the organization authentication context
   * @param tenantIdentifier the tenant identifier
   * @param queries the authentication transaction queries
   * @param requestAttributes the request attributes
   * @return the authentication transaction list response
   */
  AuthenticationTransactionManagementResponse findList(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthenticationTransactionQueries queries,
      RequestAttributes requestAttributes);

  /**
   * Gets a specific authentication transaction within the organization.
   *
   * @param authenticationContext the organization authentication context
   * @param tenantIdentifier the tenant identifier
   * @param identifier the authentication transaction identifier
   * @param requestAttributes the request attributes
   * @return the authentication transaction details response
   */
  AuthenticationTransactionManagementResponse get(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthenticationTransactionIdentifier identifier,
      RequestAttributes requestAttributes);
}
