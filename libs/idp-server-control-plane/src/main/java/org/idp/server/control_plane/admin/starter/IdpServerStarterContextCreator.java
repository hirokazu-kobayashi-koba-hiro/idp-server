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

package org.idp.server.control_plane.admin.starter;

import java.util.List;
import java.util.Map;
import org.idp.server.control_plane.admin.starter.io.IdpServerStarterRequest;
import org.idp.server.control_plane.base.definition.DefaultAdminPermission;
import org.idp.server.control_plane.base.definition.DefaultAdminRole;
import org.idp.server.control_plane.management.onboarding.io.OrganizationRegistrationRequest;
import org.idp.server.control_plane.management.onboarding.io.TenantRegistrationRequest;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserRole;
import org.idp.server.core.openid.identity.UserStatus;
import org.idp.server.core.openid.identity.authentication.PasswordEncodeDelegation;
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
import org.idp.server.platform.multi_tenancy.tenant.config.CorsConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.config.SessionConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.config.UIConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.policy.TenantIdentityPolicy;
import org.idp.server.platform.security.event.SecurityEventUserAttributeConfiguration;
import org.idp.server.platform.security.log.SecurityEventLogConfiguration;

public class IdpServerStarterContextCreator {

  IdpServerStarterRequest request;
  boolean dryRun;
  PasswordEncodeDelegation passwordEncodeDelegation;
  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  public IdpServerStarterContextCreator(
      IdpServerStarterRequest request,
      boolean dryRun,
      PasswordEncodeDelegation passwordEncodeDelegation) {
    this.request = request;
    this.dryRun = dryRun;
    this.passwordEncodeDelegation = passwordEncodeDelegation;
  }

  public IdpServerStarterContext create() {

    OrganizationRegistrationRequest organizationRequest =
        jsonConverter.read(request.get("organization"), OrganizationRegistrationRequest.class);
    TenantRegistrationRequest tenantRequest =
        jsonConverter.read(request.get("tenant"), TenantRegistrationRequest.class);
    AuthorizationServerConfiguration authorizationServerConfiguration =
        jsonConverter.read(
            request.get("authorization_server"), AuthorizationServerConfiguration.class);
    ClientConfiguration clientConfiguration =
        jsonConverter.read(request.get("client"), ClientConfiguration.class);

    Permissions permissions = DefaultAdminPermission.toPermissions();
    Roles roles = DefaultAdminRole.create(permissions);

    Organization organization = organizationRequest.toOrganization();
    TenantAttributes attributes =
        tenantRequest.attributes() != null
            ? new TenantAttributes(tenantRequest.attributes())
            : new TenantAttributes();

    UIConfiguration uiConfiguration =
        tenantRequest.uiConfig() != null
            ? new UIConfiguration(tenantRequest.uiConfig())
            : new UIConfiguration();

    CorsConfiguration corsConfiguration =
        tenantRequest.corsConfig() != null
            ? new CorsConfiguration(tenantRequest.corsConfig())
            : new CorsConfiguration();

    SessionConfiguration sessionConfiguration =
        tenantRequest.sessionConfig() != null
            ? new SessionConfiguration(tenantRequest.sessionConfig())
            : new SessionConfiguration();

    SecurityEventLogConfiguration securityEventLogConfiguration =
        tenantRequest.securityEventLogConfig() != null
            ? new SecurityEventLogConfiguration(tenantRequest.securityEventLogConfig())
            : new SecurityEventLogConfiguration();

    SecurityEventUserAttributeConfiguration securityEventUserAttributeConfiguration =
        tenantRequest.securityEventUserConfig() != null
            ? new SecurityEventUserAttributeConfiguration(tenantRequest.securityEventUserConfig())
            : new SecurityEventUserAttributeConfiguration();

    TenantIdentityPolicy identityPolicyConfig =
        convertIdentityPolicyConfig(tenantRequest.identityPolicyConfig());

    Tenant tenant =
        new Tenant(
            tenantRequest.tenantIdentifier(),
            tenantRequest.tenantName(),
            TenantType.ADMIN,
            tenantRequest.tenantDomain(),
            tenantRequest.authorizationProvider(),
            attributes,
            uiConfiguration,
            corsConfiguration,
            sessionConfiguration,
            securityEventLogConfiguration,
            securityEventUserAttributeConfiguration,
            identityPolicyConfig,
            organization.identifier());

    AssignedTenant assignedTenant =
        new AssignedTenant(tenant.identifierValue(), tenant.name().value(), tenant.type().name());
    Organization updatedWithTenant = organization.updateWithTenant(assignedTenant);

    User user = jsonConverter.read(request.get("user"), User.class);
    String encode = passwordEncodeDelegation.encode(user.rawPassword());

    // Apply tenant identity policy to set preferred_username if not set
    if (user.preferredUsername() == null || user.preferredUsername().isBlank()) {
      user.applyIdentityPolicy(tenant.identityPolicyConfig());
    }

    List<Role> rolesList = roles.toList();
    List<UserRole> userRoles =
        rolesList.stream().map(role -> new UserRole(role.id(), role.name())).toList();
    user.setHashedPassword(encode);
    User updatedUser =
        user.transitStatus(UserStatus.REGISTERED)
            .setRoles(userRoles)
            .setAssignedTenants(List.of(tenant.identifierValue()))
            .setCurrentTenantId(tenant.identifier())
            .setAssignedOrganizations(List.of(organization.identifier().value()))
            .setCurrentOrganizationId(organization.identifier());

    return new IdpServerStarterContext(
        tenant,
        authorizationServerConfiguration,
        updatedWithTenant,
        permissions,
        roles,
        updatedUser,
        clientConfiguration,
        dryRun);
  }

  private TenantIdentityPolicy convertIdentityPolicyConfig(Map<String, Object> configMap) {
    return TenantIdentityPolicy.fromMap(configMap);
  }
}
