package org.idp.server.control_plane.management.onboarding;

import org.idp.server.basic.json.JsonConverter;
import org.idp.server.control_plane.base.definition.DefinitionReader;
import org.idp.server.control_plane.management.io.OrganizationRegistrationRequest;
import org.idp.server.control_plane.management.io.TenantRegistrationRequest;
import org.idp.server.control_plane.management.onboarding.io.OnboardingRequest;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.permission.Permissions;
import org.idp.server.core.identity.role.Roles;
import org.idp.server.core.multi_tenancy.organization.Organization;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantAttributes;
import org.idp.server.core.multi_tenancy.tenant.TenantType;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;

public class OnboardingContextCreator {

  OnboardingRequest request;
  User user;
  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  public OnboardingContextCreator(OnboardingRequest request, User user) {
    this.request = request;
    this.user = user;
  }

  public OnboardingContext create() {

    OrganizationRegistrationRequest organizationRequest =
        jsonConverter.read(request.get("organization"), OrganizationRegistrationRequest.class);
    TenantRegistrationRequest tenantRequest =
        jsonConverter.read(request.get("tenant"), TenantRegistrationRequest.class);
    AuthorizationServerConfiguration authorizationServerConfiguration =
        jsonConverter.read(
            request.get("authorization_server_configuration"),
            AuthorizationServerConfiguration.class);
    ClientConfiguration clientConfiguration =
        jsonConverter.read(request.get("client"), ClientConfiguration.class);

    Permissions permissions = DefinitionReader.permissions();
    Roles roles = DefinitionReader.roles();

    User updatedUser = user.setRoles(roles.toStringList());

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

    return new OnboardingContext(
        tenant,
        authorizationServerConfiguration,
        organization,
        permissions,
        roles,
        updatedUser,
        clientConfiguration,
        request.isDryRun());
  }
}
