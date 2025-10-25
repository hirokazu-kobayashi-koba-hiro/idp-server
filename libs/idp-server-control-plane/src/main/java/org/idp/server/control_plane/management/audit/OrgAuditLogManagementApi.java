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

package org.idp.server.control_plane.management.audit;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.control_plane.base.OrganizationAccessVerifier;
import org.idp.server.control_plane.base.OrganizationAuthenticationContext;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.base.definition.DefaultAdminPermission;
import org.idp.server.control_plane.management.audit.io.AuditLogManagementResponse;
import org.idp.server.platform.audit.AuditLogIdentifier;
import org.idp.server.platform.audit.AuditLogQueries;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Organization-level audit log management API.
 *
 * <p>This interface defines operations for managing audit logs within an organization context. It
 * provides read-only operations for audit logs with organization-level access control.
 *
 * <p>Organization-level operations follow the standard access control pattern:
 *
 * <ol>
 *   <li><strong>Organization access verification</strong> - Ensures the user has access to the
 *       organization
 *   <li><strong>Permission verification</strong> - Validates the user has necessary AUDIT_LOG_READ
 *       permissions
 * </ol>
 *
 * <p>This API provides read-only access to audit log data for monitoring and audit purposes within
 * organization boundaries.
 *
 * @see AuditLogManagementApi
 * @see OrganizationAccessVerifier
 */
public interface OrgAuditLogManagementApi {

  default AdminPermissions getRequiredPermissions(String method) {
    Map<String, AdminPermissions> map = new HashMap<>();
    map.put("findList", new AdminPermissions(Set.of(DefaultAdminPermission.AUDIT_LOG_READ)));
    map.put("get", new AdminPermissions(Set.of(DefaultAdminPermission.AUDIT_LOG_READ)));
    AdminPermissions adminPermissions = map.get(method);
    if (adminPermissions == null) {
      throw new UnSupportedException("Method " + method + " not supported");
    }
    return adminPermissions;
  }

  /**
   * Lists audit logs within the organization.
   *
   * @param authenticationContext the organization authentication context
   * @param tenantIdentifier the tenant identifier
   * @param queries the audit log queries
   * @param requestAttributes the request attributes
   * @return the audit log list response
   */
  AuditLogManagementResponse findList(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuditLogQueries queries,
      RequestAttributes requestAttributes);

  /**
   * Gets a specific audit log within the organization.
   *
   * @param authenticationContext the organization authentication context
   * @param tenantIdentifier the tenant identifier
   * @param identifier the audit log identifier
   * @param requestAttributes the request attributes
   * @return the audit log details response
   */
  AuditLogManagementResponse get(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuditLogIdentifier identifier,
      RequestAttributes requestAttributes);
}
