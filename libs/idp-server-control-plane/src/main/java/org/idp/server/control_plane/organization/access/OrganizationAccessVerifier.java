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

import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.base.definition.OrganizationAdminPermissions;
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
 * <p>Usage example:
 *
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

  public OrganizationAccessVerifier() {}

  public OrganizationAccessControlResult verifyAccess(
      Organization organization,
      TenantIdentifier tenantIdentifier,
      User operator,
      AdminPermissions requiredPermissions) {

    OrganizationAccessControlResult organizationMembershipResult =
        verifyOrganizationMembership(operator, organization);
    if (!organizationMembershipResult.isSuccess()) {
      return organizationMembershipResult;
    }

    OrganizationAccessControlResult tenantAccessResult =
        verifyTenantAccess(operator, tenantIdentifier);
    if (!tenantAccessResult.isSuccess()) {
      return tenantAccessResult;
    }

    OrganizationAccessControlResult organizationTenantRelationResult =
        verifyOrganizationTenantRelation(organization, tenantIdentifier);
    if (!organizationTenantRelationResult.isSuccess()) {
      return organizationTenantRelationResult;
    }

    OrganizationAccessControlResult permissionResult =
        verifyRequiredPermissions(operator, requiredPermissions);
    if (!permissionResult.isSuccess()) {
      return permissionResult;
    }

    return OrganizationAccessControlResult.success();
  }

  private OrganizationAccessControlResult verifyOrganizationMembership(
      User operator, Organization organization) {
    if (!operator.hasAssignedOrganizations()) {
      return OrganizationAccessControlResult.forbidden("User has no assigned organizations");
    }

    if (!operator.assignedOrganizations().contains(organization.identifier().value())) {
      return OrganizationAccessControlResult.forbidden(
          String.format(
              "User is not member of organization: %s. Assigned: %s",
              organization.identifier().value(),
              String.join(",", operator.assignedOrganizations())));
    }

    return OrganizationAccessControlResult.success();
  }

  private OrganizationAccessControlResult verifyTenantAccess(
      User operator, TenantIdentifier tenantIdentifier) {

    if (!tenantIdentifier.exists()) {
      return OrganizationAccessControlResult.success();
    }

    if (!operator.hasAssignedTenants()) {
      return OrganizationAccessControlResult.forbidden("User has no assigned tenants");
    }

    if (!operator.assignedTenants().contains(tenantIdentifier.value())) {
      return OrganizationAccessControlResult.forbidden(
          String.format(
              "User does not have access to tenant: %s. Assigned: %s",
              tenantIdentifier.value(), String.join(",", operator.assignedTenants())));
    }

    return OrganizationAccessControlResult.success();
  }

  private OrganizationAccessControlResult verifyOrganizationTenantRelation(
      Organization organization, TenantIdentifier tenantIdentifier) {

    if (!tenantIdentifier.exists()) {
      return OrganizationAccessControlResult.success();
    }

    if (!organization.hasAssignedTenant(tenantIdentifier)) {
      return OrganizationAccessControlResult.forbidden(
          String.format(
              "Tenant %s is not assigned to organization %s",
              tenantIdentifier.value(), organization.identifier().value()));
    }

    return OrganizationAccessControlResult.success();
  }

  private OrganizationAccessControlResult verifyRequiredPermissions(
      User operator, AdminPermissions requiredPermissions) {
    if (!requiredPermissions.includesAll(operator.permissionsAsSet())) {
      return OrganizationAccessControlResult.forbidden(
          String.format(
              "Required permissions: %s, but user has: %s",
              requiredPermissions.valuesAsString(), operator.permissionsAsString()));
    }

    return OrganizationAccessControlResult.success();
  }
}
