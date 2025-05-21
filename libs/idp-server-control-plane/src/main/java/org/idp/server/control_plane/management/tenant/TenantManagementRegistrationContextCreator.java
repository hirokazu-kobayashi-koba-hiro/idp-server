package org.idp.server.control_plane.management.tenant;

import org.idp.server.basic.json.JsonConverter;
import org.idp.server.control_plane.management.onboarding.io.TenantRegistrationRequest;
import org.idp.server.control_plane.management.tenant.io.TenantRequest;
import org.idp.server.core.identity.User;
import org.idp.server.core.multi_tenancy.organization.AssignedTenant;
import org.idp.server.core.multi_tenancy.organization.Organization;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantAttributes;
import org.idp.server.core.multi_tenancy.tenant.TenantType;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;

public class TenantManagementRegistrationContextCreator {
  Tenant adminTenant;
  TenantRequest request;
  Organization organization;
  User user;
  boolean dryRun;
  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  public TenantManagementRegistrationContextCreator(
      Tenant adminTenant,
      TenantRequest request,
      Organization organization,
      User user,
      boolean dryRun) {
    this.adminTenant = adminTenant;
    this.request = request;
    this.organization = organization;
    this.user = user;
    this.dryRun = dryRun;
  }

  public TenantManagementRegistrationContext create() {

    TenantRegistrationRequest tenantRequest =
        jsonConverter.read(request.get("tenant"), TenantRegistrationRequest.class);
    AuthorizationServerConfiguration authorizationServerConfiguration =
        jsonConverter.read(
            request.get("authorization_server"), AuthorizationServerConfiguration.class);

    Tenant tenant =
        new Tenant(
            tenantRequest.tenantIdentifier(),
            tenantRequest.tenantName(),
            TenantType.PUBLIC,
            tenantRequest.tenantDomain(),
            tenantRequest.authorizationProvider(),
            tenantRequest.databaseType(),
            new TenantAttributes());
    AssignedTenant assignedTenant = new AssignedTenant(tenant.identifierValue(), tenant.name().value(), tenant.type().name());
    Organization assigned = organization.updateWithTenant(assignedTenant);
    user.addAssignedTenant(tenant.identifier());

    return new TenantManagementRegistrationContext(
        adminTenant, tenant, authorizationServerConfiguration, assigned, user, dryRun);
  }
}
