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

package org.idp.server.control_plane.base;

import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.exception.OrganizationAccessDeniedException;
import org.idp.server.control_plane.management.exception.PermissionDeniedException;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.multi_tenancy.organization.Organization;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

/**
 * Organization-level access control verifier.
 *
 * <p>This verifier implements a comprehensive 4-step verification process for organization-level
 * operations:
 *
 * <ol>
 *   <li><strong>Organization membership verification</strong> - Ensures the user is assigned to the
 *       organization
 *   <li><strong>Tenant access verification</strong> - Validates the user has access to the target
 *       tenant
 *   <li><strong>Organization-tenant relationship verification</strong> - Confirms the tenant is
 *       assigned to the organization
 *   <li><strong>Required permissions verification</strong> - Validates the user has necessary
 *       organization-level permissions
 * </ol>
 *
 * <p>This verification pattern ensures proper multi-tenant isolation and organization-scoped access
 * control in accordance with idp-server's security model.
 *
 * <h2>Exception-Based Pattern (Result-Exception Hybrid)</h2>
 *
 * <p>This verifier follows the standard Result-Exception Hybrid pattern used throughout the
 * codebase:
 *
 * <ul>
 *   <li><strong>Success case</strong>: Returns void (no exception)
 *   <li><strong>Failure case</strong>: Throws {@link PermissionDeniedException}
 * </ul>
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * OrganizationAccessVerifier verifier = new OrganizationAccessVerifier();
 * AdminPermissions requiredPermissions = getRequiredPermissions("create");
 *
 * try {
 *   verifier.verify(organization, tenantId, operator, requiredPermissions);
 *   // Proceed with operation
 * } catch (PermissionDeniedException e) {
 *   // Handler will wrap in Result.error()
 * }
 * }</pre>
 *
 * @see PermissionDeniedException
 * @see AdminPermissions
 */
public class OrganizationAccessVerifier {

  public OrganizationAccessVerifier() {}

  /**
   * Verifies organization-level access with 4-step verification.
   *
   * <p>This method performs comprehensive access control verification and throws {@link
   * PermissionDeniedException} on any failure. This follows the Result-Exception Hybrid pattern
   * where Verifier throws exceptions and Handler catches them to wrap in Result.
   *
   * @param organization the organization context
   * @param tenantIdentifier the target tenant identifier
   * @param operator the user performing the operation
   * @param requiredPermissions the required permissions for the operation
   * @throws OrganizationAccessDeniedException if organization membership, tenant access, or
   *     organization-tenant relationship verification fails
   * @throws PermissionDeniedException if permission verification fails
   */
  public void verify(
      Organization organization,
      TenantIdentifier tenantIdentifier,
      User operator,
      AdminPermissions requiredPermissions) {

    // Step 1: Organization membership verification
    verifyOrganizationMembership(operator, organization);

    // Step 2: Tenant access verification
    verifyTenantAccess(operator, tenantIdentifier);

    // Step 3: Organization-tenant relationship verification
    verifyOrganizationTenantRelation(organization, tenantIdentifier);

    // Step 4: Required permissions verification
    verifyRequiredPermissions(operator, requiredPermissions);
  }

  private void verifyOrganizationMembership(User operator, Organization organization) {
    if (!operator.hasAssignedOrganizations()) {
      throw new OrganizationAccessDeniedException("User has no assigned organizations");
    }

    if (!operator.assignedOrganizations().contains(organization.identifier().value())) {
      throw new OrganizationAccessDeniedException(
          String.format(
              "User is not member of organization: %s. Assigned: %s",
              organization.identifier().value(),
              String.join(",", operator.assignedOrganizations())));
    }
  }

  private void verifyTenantAccess(User operator, TenantIdentifier tenantIdentifier) {

    if (!tenantIdentifier.exists()) {
      return;
    }

    if (!operator.hasAssignedTenants()) {
      throw new OrganizationAccessDeniedException("User has no assigned tenants");
    }

    if (!operator.assignedTenants().contains(tenantIdentifier.value())) {
      throw new OrganizationAccessDeniedException(
          String.format(
              "User does not have access to tenant: %s. Assigned: %s",
              tenantIdentifier.value(), String.join(",", operator.assignedTenants())));
    }
  }

  private void verifyOrganizationTenantRelation(
      Organization organization, TenantIdentifier tenantIdentifier) {

    if (!tenantIdentifier.exists()) {
      return;
    }

    if (!organization.hasAssignedTenant(tenantIdentifier)) {
      throw new OrganizationAccessDeniedException(
          String.format(
              "Tenant %s is not assigned to organization %s",
              tenantIdentifier.value(), organization.identifier().value()));
    }
  }

  private void verifyRequiredPermissions(User operator, AdminPermissions requiredPermissions) {
    if (!requiredPermissions.includesAll(operator.permissionsAsSet())) {
      throw new PermissionDeniedException(requiredPermissions, operator.permissionsAsSet());
    }
  }
}
