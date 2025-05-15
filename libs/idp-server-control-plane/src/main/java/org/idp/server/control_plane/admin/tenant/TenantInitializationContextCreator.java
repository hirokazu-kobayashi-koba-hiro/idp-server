package org.idp.server.control_plane.admin.tenant;

import org.idp.server.basic.json.JsonConverter;
import org.idp.server.control_plane.admin.tenant.io.TenantInitializationRequest;
import org.idp.server.control_plane.base.definition.AdminPermission;
import org.idp.server.control_plane.base.definition.AdminRole;
import org.idp.server.control_plane.management.onboarding.io.OrganizationRegistrationRequest;
import org.idp.server.control_plane.management.onboarding.io.TenantRegistrationRequest;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserStatus;
import org.idp.server.core.identity.authentication.PasswordEncodeDelegation;
import org.idp.server.core.identity.permission.Permissions;
import org.idp.server.core.identity.role.Roles;
import org.idp.server.core.multi_tenancy.organization.Organization;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantAttributes;
import org.idp.server.core.multi_tenancy.tenant.TenantType;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;

public class TenantInitializationContextCreator {

  TenantInitializationRequest request;
  boolean dryRun;
  PasswordEncodeDelegation passwordEncodeDelegation;
  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  public TenantInitializationContextCreator(
      TenantInitializationRequest request,
      boolean dryRun,
      PasswordEncodeDelegation passwordEncodeDelegation) {
    this.request = request;
    this.dryRun = dryRun;
    this.passwordEncodeDelegation = passwordEncodeDelegation;
  }

  public TenantInitializationContext create() {

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

    User user = jsonConverter.read(request.get("user"), User.class);
    String encode = passwordEncodeDelegation.encode(user.rawPassword());
    user.setHashedPassword(encode);
    User updatedUser = user.transitStatus(UserStatus.REGISTERED);

    Organization organization = organizationRequest.toOrganization();
    Tenant tenant =
        new Tenant(
            tenantRequest.tenantIdentifier(),
            tenantRequest.tenantName(),
            TenantType.PUBLIC,
            tenantRequest.tenantDomain(),
            tenantRequest.authorizationProvider(),
            tenantRequest.databaseType(),
            TenantAttributes.createDefaultType());
    organization.assign(tenant);

    return new TenantInitializationContext(
        tenant,
        authorizationServerConfiguration,
        organization,
        permissions,
        roles,
        updatedUser,
        clientConfiguration,
        dryRun);
  }
}
