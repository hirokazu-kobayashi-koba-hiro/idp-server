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

package org.idp.server.control_plane.management.identity.user.verifier;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.idp.server.control_plane.base.verifier.VerificationResult;
import org.idp.server.control_plane.management.identity.user.io.UserRegistrationRequest;
import org.idp.server.core.openid.identity.UserRole;
import org.idp.server.core.openid.identity.role.Role;
import org.idp.server.core.openid.identity.role.RoleQueryRepository;
import org.idp.server.platform.multi_tenancy.organization.Organization;
import org.idp.server.platform.multi_tenancy.organization.OrganizationQueries;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;

public class UserRegistrationUpdateVerifier {

  private final RoleQueryRepository roleQueryRepository;
  private final TenantQueryRepository tenantQueryRepository;
  private final OrganizationRepository organizationRepository;

  public UserRegistrationUpdateVerifier(
      RoleQueryRepository roleQueryRepository,
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository) {
    this.roleQueryRepository = roleQueryRepository;
    this.tenantQueryRepository = tenantQueryRepository;
    this.organizationRepository = organizationRepository;
  }

  public VerificationResult verifyRoles(Tenant tenant, UserRegistrationRequest request) {
    List<String> errors = new ArrayList<>();

    try {
      List<UserRole> roles = Optional.ofNullable(request.roles()).orElse(List.of());

      // Extract unique role IDs to avoid duplicate queries
      Set<String> uniqueRoleIds = new LinkedHashSet<>();
      for (UserRole userRole : roles) {
        String roleId = userRole.roleId();
        if (roleId == null || roleId.trim().isEmpty()) {
          errors.add("field=role_id, error=required, message=Role ID cannot be null or empty");
          continue;
        }

        if (!isValidUUID(roleId)) {
          errors.add(
              "field=role_id, error=invalid_uuid, value="
                  + roleId
                  + ", message=Role ID must be a valid UUID");
          continue;
        }

        uniqueRoleIds.add(roleId);
      }

      // Bulk existence check for roles - avoid N+1 query problem
      if (!uniqueRoleIds.isEmpty()) {
        try {
          org.idp.server.core.openid.identity.role.Roles allRoles =
              roleQueryRepository.findAll(tenant);
          Set<String> existingRoleIds =
              allRoles.toList().stream().map(Role::id).collect(java.util.stream.Collectors.toSet());

          // Check which requested roles don't exist
          for (String roleId : uniqueRoleIds) {
            if (!existingRoleIds.contains(roleId)) {
              errors.add(
                  "field=role_id, error=not_found, value="
                      + roleId
                      + ", message=Role does not exist");
            }
          }
        } catch (Exception e) {
          // Re-throw database/connection errors as 5xx
          throw new RuntimeException("Database error while validating roles", e);
        }
      }
    } catch (RuntimeException e) {
      // Re-throw database errors
      throw e;
    } catch (Exception e) {
      errors.add("field=roles, error=validation_error, message=Unexpected error validating roles");
    }

    if (!errors.isEmpty()) {
      return VerificationResult.failure(errors);
    }

    return VerificationResult.success();
  }

  public VerificationResult verifyTenantAssignments(UserRegistrationRequest request) {
    List<String> errors = new ArrayList<>();

    try {
      String currentTenantId = request.currentTenant();
      List<String> assignedTenants =
          Optional.ofNullable(request.assignedTenants()).orElse(List.of());

      // Deduplicate assigned tenants
      Set<String> uniqueAssignedTenantIds = new LinkedHashSet<>(assignedTenants);

      // Validate current tenant ID format
      if (currentTenantId != null && !currentTenantId.trim().isEmpty()) {
        if (!isValidUUID(currentTenantId)) {
          errors.add(
              "field=current_tenant_id, error=invalid_uuid, value="
                  + currentTenantId
                  + ", message=Current tenant ID must be a valid UUID");
        }
      }

      // Validate assigned tenant IDs format
      Set<String> validAssignedTenantIds = new LinkedHashSet<>();
      for (String tenantId : uniqueAssignedTenantIds) {
        if (tenantId == null || tenantId.trim().isEmpty()) {
          errors.add(
              "field=assigned_tenants, error=required, message=Assigned tenant ID cannot be null or empty");
          continue;
        }

        if (!isValidUUID(tenantId)) {
          errors.add(
              "field=assigned_tenants, error=invalid_uuid, value="
                  + tenantId
                  + ", message=Assigned tenant ID must be a valid UUID");
          continue;
        }

        validAssignedTenantIds.add(tenantId);
      }

      // Data consistency check: current_tenant_id should be in assigned_tenants
      if (currentTenantId != null
          && !currentTenantId.trim().isEmpty()
          && !validAssignedTenantIds.contains(currentTenantId)) {
        errors.add(
            "field=current_tenant_id, error=consistency_violation, value="
                + currentTenantId
                + ", message=Current tenant must be included in assigned tenants");
      }

      // Bulk existence check for all tenant IDs
      Set<String> allTenantIds = new LinkedHashSet<>(validAssignedTenantIds);
      if (currentTenantId != null
          && !currentTenantId.trim().isEmpty()
          && isValidUUID(currentTenantId)) {
        allTenantIds.add(currentTenantId);
      }

      // Bulk existence check for tenants - avoid N+1 query problem
      if (!allTenantIds.isEmpty()) {
        try {
          List<TenantIdentifier> tenantIdentifiers =
              allTenantIds.stream()
                  .map(TenantIdentifier::new)
                  .collect(java.util.stream.Collectors.toList());

          List<Tenant> existingTenants = tenantQueryRepository.findList(tenantIdentifiers);
          Set<String> existingTenantIds =
              existingTenants.stream()
                  .filter(Tenant::exists)
                  .map(tenant -> tenant.identifier().value())
                  .collect(java.util.stream.Collectors.toSet());

          // Check which requested tenants don't exist
          for (String tenantId : allTenantIds) {
            if (!existingTenantIds.contains(tenantId)) {
              errors.add(
                  "field=tenant_id, error=not_found, value="
                      + tenantId
                      + ", message=Tenant does not exist");
            }
          }
        } catch (Exception e) {
          // Re-throw database/connection errors as 5xx
          throw new RuntimeException("Database error while validating tenants", e);
        }
      }
    } catch (RuntimeException e) {
      // Re-throw database errors
      throw e;
    } catch (Exception e) {
      errors.add(
          "field=tenant_assignments, error=validation_error, message=Unexpected error validating tenant assignments");
    }

    if (!errors.isEmpty()) {
      return VerificationResult.failure(errors);
    }

    return VerificationResult.success();
  }

  public VerificationResult verifyOrganizationAssignments(
      Tenant tenant, UserRegistrationRequest request) {
    List<String> errors = new ArrayList<>();

    try {
      String currentOrganizationId = request.currentOrganizationId();
      List<String> assignedOrganizations =
          Optional.ofNullable(request.assignedOrganizations()).orElse(List.of());

      // Deduplicate assigned organizations
      Set<String> uniqueAssignedOrgIds = new LinkedHashSet<>(assignedOrganizations);

      // Validate current organization ID format
      if (currentOrganizationId != null && !currentOrganizationId.trim().isEmpty()) {
        if (!isValidUUID(currentOrganizationId)) {
          errors.add(
              "field=current_organization_id, error=invalid_uuid, value="
                  + currentOrganizationId
                  + ", message=Current organization ID must be a valid UUID");
        }
      }

      // Validate assigned organization IDs format
      Set<String> validAssignedOrgIds = new LinkedHashSet<>();
      for (String organizationId : uniqueAssignedOrgIds) {
        if (organizationId == null || organizationId.trim().isEmpty()) {
          errors.add(
              "field=assigned_organizations, error=required, message=Assigned organization ID cannot be null or empty");
          continue;
        }

        if (!isValidUUID(organizationId)) {
          errors.add(
              "field=assigned_organizations, error=invalid_uuid, value="
                  + organizationId
                  + ", message=Assigned organization ID must be a valid UUID");
          continue;
        }

        validAssignedOrgIds.add(organizationId);
      }

      // Data consistency check: current_organization_id should be in assigned_organizations
      if (currentOrganizationId != null
          && !currentOrganizationId.trim().isEmpty()
          && !validAssignedOrgIds.contains(currentOrganizationId)) {
        errors.add(
            "field=current_organization_id, error=consistency_violation, value="
                + currentOrganizationId
                + ", message=Current organization must be included in assigned organizations");
      }

      // Bulk existence check for all organization IDs
      Set<String> allOrgIds = new LinkedHashSet<>(validAssignedOrgIds);
      if (currentOrganizationId != null
          && !currentOrganizationId.trim().isEmpty()
          && isValidUUID(currentOrganizationId)) {
        allOrgIds.add(currentOrganizationId);
      }

      // Bulk existence check for organizations - avoid N+1 query problem
      if (!allOrgIds.isEmpty()) {
        try {
          OrganizationQueries queries = OrganizationQueries.ids(allOrgIds);
          List<Organization> existingOrganizations =
              organizationRepository.findList(tenant, queries);
          Set<String> existingOrgIds =
              existingOrganizations.stream()
                  .filter(Organization::exists)
                  .map(org -> org.identifier().value())
                  .collect(java.util.stream.Collectors.toSet());

          // Check which requested organizations don't exist
          for (String organizationId : allOrgIds) {
            if (!existingOrgIds.contains(organizationId)) {
              errors.add(
                  "field=organization_id, error=not_found, value="
                      + organizationId
                      + ", message=Organization does not exist");
            }
          }
        } catch (Exception e) {
          // Re-throw database/connection errors as 5xx
          throw new RuntimeException("Database error while validating organizations", e);
        }
      }
    } catch (RuntimeException e) {
      // Re-throw database errors
      throw e;
    } catch (Exception e) {
      errors.add(
          "field=organization_assignments, error=validation_error, message=Unexpected error validating organization assignments");
    }

    if (!errors.isEmpty()) {
      return VerificationResult.failure(errors);
    }

    return VerificationResult.success();
  }

  private boolean isValidUUID(String value) {
    if (value == null || value.trim().isEmpty()) {
      return false;
    }
    try {
      UUID.fromString(value);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
