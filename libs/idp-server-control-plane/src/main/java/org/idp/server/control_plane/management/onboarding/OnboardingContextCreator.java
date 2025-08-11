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

package org.idp.server.control_plane.management.onboarding;

import java.util.List;
import org.idp.server.control_plane.base.definition.AdminPermission;
import org.idp.server.control_plane.base.definition.AdminRole;
import org.idp.server.control_plane.management.onboarding.io.OnboardingRequest;
import org.idp.server.control_plane.management.onboarding.io.OrganizationRegistrationRequest;
import org.idp.server.control_plane.management.onboarding.io.TenantRegistrationRequest;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserRole;
import org.idp.server.core.openid.identity.permission.Permissions;
import org.idp.server.core.openid.identity.role.Role;
import org.idp.server.core.openid.identity.role.Roles;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.organization.AssignedTenant;
import org.idp.server.platform.multi_tenancy.organization.Organization;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantAttributes;
import org.idp.server.platform.multi_tenancy.tenant.TenantType;

public class OnboardingContextCreator {

  OnboardingRequest request;
  User user;
  boolean dryRun;
  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  public OnboardingContextCreator(OnboardingRequest request, User user, boolean dryRun) {
    this.request = request;
    this.user = user;
    this.dryRun = dryRun;
  }

  public OnboardingContext create() {

    OrganizationRegistrationRequest organizationRequest =
        jsonConverter.read(request.get("organization"), OrganizationRegistrationRequest.class);
    TenantRegistrationRequest tenantRequest =
        jsonConverter.read(request.get("tenant"), TenantRegistrationRequest.class);
    AuthorizationServerConfiguration authorizationServerConfiguration =
        jsonConverter.read(
            request.get("authorization_server"), AuthorizationServerConfiguration.class);
    ClientConfiguration clientConfiguration =
        jsonConverter.read(request.get("client"), ClientConfiguration.class);

    Permissions permissions = AdminPermission.toPermissions();
    Roles roles = AdminRole.toRoles();

    Organization organization = organizationRequest.toOrganization();
    Tenant tenant =
        new Tenant(
            tenantRequest.tenantIdentifier(),
            tenantRequest.tenantName(),
            TenantType.PUBLIC,
            tenantRequest.tenantDomain(),
            tenantRequest.authorizationProvider(),
            tenantRequest.databaseType(),
            new TenantAttributes());

    AssignedTenant assignedTenant =
        new AssignedTenant(tenant.identifierValue(), tenant.name().value(), tenant.type().name());
    Organization assigned = organization.updateWithTenant(assignedTenant);

    List<Role> rolesList = roles.toList();
    List<UserRole> userRoles =
        rolesList.stream().map(role -> new UserRole(role.id(), role.name())).toList();
    User updatedUser =
        user.setRoles(userRoles).setAssignedTenants(List.of(tenant.identifierValue()));

    return new OnboardingContext(
        tenant,
        authorizationServerConfiguration,
        assigned,
        permissions,
        roles,
        updatedUser,
        clientConfiguration,
        dryRun);
  }
}
