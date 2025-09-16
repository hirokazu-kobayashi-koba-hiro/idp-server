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
import org.idp.server.control_plane.base.verifier.VerificationResult;
import org.idp.server.control_plane.management.identity.user.io.UserRegistrationRequest;
import org.idp.server.core.openid.identity.UserRole;
import org.idp.server.core.openid.identity.role.Role;
import org.idp.server.core.openid.identity.role.RoleQueryRepository;
import org.idp.server.core.openid.identity.role.Roles;
import org.idp.server.platform.multi_tenancy.organization.Organization;
import org.idp.server.platform.multi_tenancy.organization.OrganizationQueries;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;

public class UserRegistrationRelatedDataVerifier {

  private final RoleQueryRepository roleQueryRepository;
  private final TenantQueryRepository tenantQueryRepository;
  private final OrganizationRepository organizationRepository;

  public UserRegistrationRelatedDataVerifier(
      RoleQueryRepository roleQueryRepository,
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository) {
    this.roleQueryRepository = roleQueryRepository;
    this.tenantQueryRepository = tenantQueryRepository;
    this.organizationRepository = organizationRepository;
  }

  public VerificationResult verifyRoles(Tenant tenant, UserRegistrationRequest request) {
    List<String> errors = new ArrayList<>();
    List<UserRole> roles = Optional.ofNullable(request.roles()).orElse(List.of());

    // Extract unique role IDs to avoid duplicate queries
    Set<String> uniqueRoleIds = new LinkedHashSet<>();
    for (UserRole userRole : roles) {
      String roleId = userRole.roleId();
      uniqueRoleIds.add(roleId);
    }

    if (uniqueRoleIds.isEmpty()) {
      return VerificationResult.success();
    }

    // Bulk existence check for roles - avoid N+1 query problem
    Roles allRoles = roleQueryRepository.findAll(tenant);
    Set<String> existingRoleIds =
        allRoles.toList().stream().map(Role::id).collect(java.util.stream.Collectors.toSet());

    // Check which requested roles don't exist
    for (String roleId : uniqueRoleIds) {
      if (!existingRoleIds.contains(roleId)) {
        errors.add(
            "field=role_id, error=not_found, value=" + roleId + ", message=Role does not exist");
      }
    }

    if (!errors.isEmpty()) {
      return VerificationResult.failure(errors);
    }

    return VerificationResult.success();
  }

  public VerificationResult verifyTenantAssignments(UserRegistrationRequest request) {
    List<String> errors = new ArrayList<>();

    String currentTenantId = request.currentTenant();
    List<String> assignedTenants = Optional.ofNullable(request.assignedTenants()).orElse(List.of());

    // Deduplicate assigned tenants
    Set<String> uniqueAssignedTenantIds = new LinkedHashSet<>(assignedTenants);

    // Bulk existence check for all tenant IDs
    Set<String> allTenantIds = new LinkedHashSet<>(uniqueAssignedTenantIds);
    if (currentTenantId != null && !currentTenantId.trim().isEmpty()) {
      allTenantIds.add(currentTenantId);
    }

    if (allTenantIds.isEmpty()) {
      return VerificationResult.success();
    }

    // Bulk existence check for tenants - avoid N+1 query problem
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

    if (!errors.isEmpty()) {
      return VerificationResult.failure(errors);
    }

    return VerificationResult.success();
  }

  public VerificationResult verifyOrganizationAssignments(
      Tenant tenant, UserRegistrationRequest request) {
    List<String> errors = new ArrayList<>();

    String currentOrganizationId = request.currentOrganizationId();
    List<String> assignedOrganizations =
        Optional.ofNullable(request.assignedOrganizations()).orElse(List.of());

    // Deduplicate assigned organizations
    Set<String> uniqueAssignedOrgIds = new LinkedHashSet<>(assignedOrganizations);
    if (uniqueAssignedOrgIds.isEmpty()) {
      return VerificationResult.success();
    }

    // Bulk existence check for all organization IDs
    Set<String> allOrgIds = new LinkedHashSet<>(uniqueAssignedOrgIds);
    allOrgIds.add(currentOrganizationId);

    // Bulk existence check for organizations - avoid N+1 query problem
    OrganizationQueries queries = OrganizationQueries.ids(allOrgIds);
    List<Organization> existingOrganizations = organizationRepository.findList(queries);
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

    if (!errors.isEmpty()) {
      return VerificationResult.failure(errors);
    }

    return VerificationResult.success();
  }
}
