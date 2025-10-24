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

package org.idp.server.control_plane.management.identity.verification;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.control_plane.base.OrganizationAccessVerifier;
import org.idp.server.control_plane.base.OrganizationAuthenticationContext;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.base.definition.DefaultAdminPermission;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigManagementResponse;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigRegistrationRequest;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigUpdateRequest;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfigurationIdentifier;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationQueries;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Organization-level Identity Verification Configuration Management API interface.
 *
 * <p>This API provides organization-scoped management operations for identity verification
 * configurations within a specific tenant. All operations require organization-level administrative
 * permissions and proper organization access verification.
 *
 * <p>The API supports complete CRUD operations with dry-run functionality:
 *
 * <ul>
 *   <li><strong>CREATE</strong> - Register new identity verification configurations
 *   <li><strong>READ</strong> - Retrieve individual configurations and filtered lists
 *   <li><strong>UPDATE</strong> - Modify existing configurations
 *   <li><strong>DELETE</strong> - Remove configurations
 * </ul>
 *
 * <p>All operations include comprehensive audit logging and support dry-run mode for safe
 * configuration preview before actual changes.
 *
 * @see IdentityVerificationConfigManagementApi
 * @see AdminPermissions
 * @see OrganizationAccessVerifier
 */
public interface OrgIdentityVerificationConfigManagementApi {

  /**
   * Returns the required organization-level permissions for each API operation.
   *
   * @param method the API method name
   * @return required organization admin permissions
   */
  default AdminPermissions getRequiredPermissions(String method) {
    Map<String, AdminPermissions> map = new HashMap<>();
    map.put(
        "create",
        new AdminPermissions(Set.of(DefaultAdminPermission.IDENTITY_VERIFICATION_CONFIG_CREATE)));
    map.put(
        "findList",
        new AdminPermissions(Set.of(DefaultAdminPermission.IDENTITY_VERIFICATION_CONFIG_READ)));
    map.put(
        "get",
        new AdminPermissions(Set.of(DefaultAdminPermission.IDENTITY_VERIFICATION_CONFIG_READ)));
    map.put(
        "update",
        new AdminPermissions(Set.of(DefaultAdminPermission.IDENTITY_VERIFICATION_CONFIG_UPDATE)));
    map.put(
        "delete",
        new AdminPermissions(Set.of(DefaultAdminPermission.IDENTITY_VERIFICATION_CONFIG_DELETE)));
    AdminPermissions adminPermissions = map.get(method);
    if (adminPermissions == null) {
      throw new UnSupportedException("Method " + method + " not supported");
    }
    return adminPermissions;
  }

  /**
   * Creates a new identity verification configuration within the organization tenant.
   *
   * @param organizationIdentifier the organization identifier
   * @param tenantIdentifier the target tenant identifier within the organization
   * @param operator the user performing the operation
   * @param oAuthToken the authentication token
   * @param request the identity verification configuration registration request
   * @param requestAttributes additional request context information
   * @param dryRun whether to perform a dry-run (preview) operation
   * @return the creation response with configuration details
   */
  IdentityVerificationConfigManagementResponse create(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      IdentityVerificationConfigRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  IdentityVerificationConfigManagementResponse findList(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      IdentityVerificationQueries queries,
      RequestAttributes requestAttributes);

  IdentityVerificationConfigManagementResponse get(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      IdentityVerificationConfigurationIdentifier identifier,
      RequestAttributes requestAttributes);

  IdentityVerificationConfigManagementResponse update(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      IdentityVerificationConfigurationIdentifier identifier,
      IdentityVerificationConfigUpdateRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  IdentityVerificationConfigManagementResponse delete(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      IdentityVerificationConfigurationIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun);
}
