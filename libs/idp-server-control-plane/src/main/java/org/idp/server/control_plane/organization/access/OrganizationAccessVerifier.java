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

package org.idp.server.control_plane.organization.access;

import org.idp.server.control_plane.base.definition.OrganizationAdminPermissions;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.multi_tenancy.organization.AssignedTenant;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

/**
 * Organization-level access control verifier.
 *
 * <p>This verifier implements a comprehensive 4-step verification process for organization-level
 * operations:
 * <ol>
 *   <li><strong>Organization membership verification</strong> - Ensures the user is assigned to the organization</li>
 *   <li><strong>Tenant access verification</strong> - Validates the user has access to the target tenant</li>
 *   <li><strong>Organization-tenant relationship verification</strong> - Confirms the tenant is assigned to the organization</li>
 *   <li><strong>Required permissions verification</strong> - Validates the user has necessary organization-level permissions</li>
 * </ol>
 *
 * <p>This verification pattern ensures proper multi-tenant isolation and organization-scoped
 * access control in accordance with idp-server's security model.
 *
 * <p>Usage example:
 * <pre>{@code
 * OrganizationAccessVerifier verifier = new OrganizationAccessVerifier(orgRepository);
 * OrganizationAdminPermissions requiredPermissions = new OrganizationAdminPermissions(
 *     Set.of(OrganizationAdminPermission.ORG_TENANT_CREATE)
 * );
 * 
 * OrganizationAccessControlResult result = verifier.verifyAccess(
 *     organizationId, tenantId, operator, requiredPermissions, adminTenant
 * );
 * 
 * if (result.isSuccess()) {
 *     // Proceed with operation
 * } else {
 *     // Handle access denied or not found
 * }
 * }</pre>
 *
 * @see OrganizationAccessControlResult
 * @see OrganizationAdminPermissions
 */
public class OrganizationAccessVerifier {

  private final OrganizationRepository organizationRepository;

  public OrganizationAccessVerifier(OrganizationRepository organizationRepository) {
    this.organizationRepository = organizationRepository;
  }

  public OrganizationAccessControlResult verifyAccess(
      OrganizationIdentifier organizationId,
      TenantIdentifier tenantId,
      User operator,
      OrganizationAdminPermissions requiredPermissions,
      Tenant adminTenant) {

    OrganizationAccessControlResult organizationMembershipResult =
        verifyOrganizationMembership(operator, organizationId);
    if (!organizationMembershipResult.isSuccess()) {
      return organizationMembershipResult;
    }

    OrganizationAccessControlResult tenantAccessResult = verifyTenantAccess(operator, tenantId);
    if (!tenantAccessResult.isSuccess()) {
      return tenantAccessResult;
    }

    OrganizationAccessControlResult organizationTenantRelationResult =
        verifyOrganizationTenantRelation(adminTenant, organizationId, tenantId);
    if (!organizationTenantRelationResult.isSuccess()) {
      return organizationTenantRelationResult;
    }

    OrganizationAccessControlResult permissionResult =
        verifyRequiredPermissions(operator, requiredPermissions);
    if (!permissionResult.isSuccess()) {
      return permissionResult;
    }

    AssignedTenant assignment =
        organizationRepository.findAssignment(adminTenant, organizationId, tenantId);
    return OrganizationAccessControlResult.success(operator, assignment);
  }

  private OrganizationAccessControlResult verifyOrganizationMembership(
      User operator, OrganizationIdentifier organizationId) {
    if (!operator.hasAssignedOrganizations()) {
      return OrganizationAccessControlResult.forbidden("User has no assigned organizations");
    }

    if (!operator.assignedOrganizations().contains(organizationId.value())) {
      return OrganizationAccessControlResult.forbidden(
          String.format(
              "User is not member of organization: %s. Assigned: %s",
              organizationId.value(), String.join(",", operator.assignedOrganizations())));
    }

    return OrganizationAccessControlResult.success(operator, null);
  }

  private OrganizationAccessControlResult verifyTenantAccess(
      User operator, TenantIdentifier tenantId) {
    if (!operator.hasAssignedTenants()) {
      return OrganizationAccessControlResult.forbidden("User has no assigned tenants");
    }

    if (!operator.assignedTenants().contains(tenantId.value())) {
      return OrganizationAccessControlResult.forbidden(
          String.format(
              "User does not have access to tenant: %s. Assigned: %s",
              tenantId.value(), String.join(",", operator.assignedTenants())));
    }

    return OrganizationAccessControlResult.success(operator, null);
  }

  private OrganizationAccessControlResult verifyOrganizationTenantRelation(
      Tenant adminTenant, OrganizationIdentifier organizationId, TenantIdentifier tenantId) {
    AssignedTenant assignment =
        organizationRepository.findAssignment(adminTenant, organizationId, tenantId);
    if (assignment == null || !assignment.exists()) {
      return OrganizationAccessControlResult.notFound(
          String.format(
              "Tenant %s is not assigned to organization %s",
              tenantId.value(), organizationId.value()));
    }

    return OrganizationAccessControlResult.success(null, assignment);
  }

  private OrganizationAccessControlResult verifyRequiredPermissions(
      User operator, OrganizationAdminPermissions requiredPermissions) {
    if (!requiredPermissions.includesAll(operator.permissionsAsSet())) {
      return OrganizationAccessControlResult.forbidden(
          String.format(
              "Required permissions: %s, but user has: %s",
              requiredPermissions.valuesAsString(), operator.permissionsAsString()));
    }

    return OrganizationAccessControlResult.success(operator, null);
  }
}
