package org.idp.server.control_plane.admin.starter;

import java.util.List;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.control_plane.admin.starter.io.IdpServerStarterRequest;
import org.idp.server.control_plane.base.definition.AdminPermission;
import org.idp.server.control_plane.base.definition.AdminRole;
import org.idp.server.control_plane.management.onboarding.io.OrganizationRegistrationRequest;
import org.idp.server.control_plane.management.onboarding.io.TenantRegistrationRequest;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserRole;
import org.idp.server.core.identity.UserStatus;
import org.idp.server.core.identity.authentication.PasswordEncodeDelegation;
import org.idp.server.core.identity.permission.Permissions;
import org.idp.server.core.identity.role.Role;
import org.idp.server.core.identity.role.Roles;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.platform.multi_tenancy.organization.AssignedTenant;
import org.idp.server.platform.multi_tenancy.organization.Organization;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantAttributes;
import org.idp.server.platform.multi_tenancy.tenant.TenantType;

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

    Permissions permissions = AdminPermission.toPermissions();
    Roles roles = AdminRole.toRoles();

    Organization organization = organizationRequest.toOrganization();
    Tenant tenant =
        new Tenant(
            tenantRequest.tenantIdentifier(),
            tenantRequest.tenantName(),
            TenantType.ADMIN,
            tenantRequest.tenantDomain(),
            tenantRequest.authorizationProvider(),
            tenantRequest.databaseType(),
            new TenantAttributes());

    AssignedTenant assignedTenant =
        new AssignedTenant(tenant.identifierValue(), tenant.name().value(), tenant.type().name());
    Organization updatedWithTenant = organization.updateWithTenant(assignedTenant);

    User user = jsonConverter.read(request.get("user"), User.class);
    String encode = passwordEncodeDelegation.encode(user.rawPassword());

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
}
