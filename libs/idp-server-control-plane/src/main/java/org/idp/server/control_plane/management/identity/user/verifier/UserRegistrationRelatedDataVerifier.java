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
import org.idp.server.control_plane.management.exception.InvalidRequestException;
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

  public void verifyRoles(Tenant tenant, UserRegistrationRequest request) {
    List<UserRole> roles = Optional.ofNullable(request.roles()).orElse(List.of());

    Set<String> uniqueRoleIds = new LinkedHashSet<>();
    for (UserRole userRole : roles) {
      uniqueRoleIds.add(userRole.roleId());
    }

    if (uniqueRoleIds.isEmpty()) {
      return;
    }

    Roles allRoles = roleQueryRepository.findAll(tenant);
    Set<String> existingRoleIds =
        allRoles.toList().stream().map(Role::id).collect(java.util.stream.Collectors.toSet());

    List<String> errors = new ArrayList<>();
    for (String roleId : uniqueRoleIds) {
      if (!existingRoleIds.contains(roleId)) {
        errors.add(
            "field=role_id, error=not_found, value=" + roleId + ", message=Role does not exist");
      }
    }

    throwExceptionIfInvalidRoles(errors);
  }

  void throwExceptionIfInvalidRoles(List<String> errors) {
    if (!errors.isEmpty()) {
      throw new InvalidRequestException("roles verification is failed", errors);
    }
  }

  public void verifyTenantAssignments(UserRegistrationRequest request) {
    String currentTenantId = request.currentTenant();
    List<String> assignedTenants = Optional.ofNullable(request.assignedTenants()).orElse(List.of());

    Set<String> allTenantIds = new LinkedHashSet<>(assignedTenants);
    if (currentTenantId != null && !currentTenantId.trim().isEmpty()) {
      allTenantIds.add(currentTenantId);
    }

    if (allTenantIds.isEmpty()) {
      return;
    }

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

    List<String> errors = new ArrayList<>();
    for (String tenantId : allTenantIds) {
      if (!existingTenantIds.contains(tenantId)) {
        errors.add(
            "field=tenant_id, error=not_found, value="
                + tenantId
                + ", message=Tenant does not exist");
      }
    }

    throwExceptionIfInvalidTenantAssignments(errors);
  }

  void throwExceptionIfInvalidTenantAssignments(List<String> errors) {
    if (!errors.isEmpty()) {
      throw new InvalidRequestException("tenant assignments verification is failed", errors);
    }
  }

  public void verifyOrganizationAssignments(Tenant tenant, UserRegistrationRequest request) {
    String currentOrganizationId = request.currentOrganizationId();
    List<String> assignedOrganizations =
        Optional.ofNullable(request.assignedOrganizations()).orElse(List.of());

    Set<String> allOrgIds = new LinkedHashSet<>(assignedOrganizations);
    if (currentOrganizationId != null && !currentOrganizationId.trim().isEmpty()) {
      allOrgIds.add(currentOrganizationId);
    }

    if (allOrgIds.isEmpty()) {
      return;
    }

    OrganizationQueries queries = OrganizationQueries.ids(allOrgIds);
    List<Organization> existingOrganizations = organizationRepository.findList(queries);
    Set<String> existingOrgIds =
        existingOrganizations.stream()
            .filter(Organization::exists)
            .map(org -> org.identifier().value())
            .collect(java.util.stream.Collectors.toSet());

    List<String> errors = new ArrayList<>();
    for (String organizationId : allOrgIds) {
      if (!existingOrgIds.contains(organizationId)) {
        errors.add(
            "field=organization_id, error=not_found, value="
                + organizationId
                + ", message=Organization does not exist");
      }
    }

    throwExceptionIfInvalidOrganizationAssignments(errors);
  }

  void throwExceptionIfInvalidOrganizationAssignments(List<String> errors) {
    if (!errors.isEmpty()) {
      throw new InvalidRequestException("organization assignments verification is failed", errors);
    }
  }
}
