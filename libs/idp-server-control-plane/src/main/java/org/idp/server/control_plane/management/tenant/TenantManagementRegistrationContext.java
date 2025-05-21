package org.idp.server.control_plane.management.tenant;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.management.tenant.io.TenantManagementResponse;
import org.idp.server.control_plane.management.tenant.io.TenantManagementStatus;
import org.idp.server.core.identity.User;
import org.idp.server.core.multi_tenancy.organization.Organization;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;

public class TenantManagementRegistrationContext {

  Tenant adminTenant;
  Tenant newTenant;
  AuthorizationServerConfiguration authorizationServerConfiguration;
  Organization organization;
  User user;
  boolean dryRun;

  public TenantManagementRegistrationContext(
      Tenant adminTenant,
      Tenant newTenant,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      Organization organization,
      User user,
      boolean dryRun) {
    this.adminTenant = adminTenant;
    this.newTenant = newTenant;
    this.authorizationServerConfiguration = authorizationServerConfiguration;
    this.organization = organization;
    this.user = user;
    this.dryRun = dryRun;
  }

  public Tenant adminTenant() {
    return adminTenant;
  }

  public Tenant newTenant() {
    return newTenant;
  }

  public AuthorizationServerConfiguration authorizationServerConfiguration() {
    return authorizationServerConfiguration;
  }

  public Organization organization() {
    return organization;
  }

  public User user() {
    return user;
  }

  public boolean isDryRun() {
    return dryRun;
  }

  public TenantManagementResponse toResponse() {
    Map<String, Object> contents = new HashMap<>();
    contents.put("result", newTenant.toMap());
    contents.put("dry_run", dryRun);
    return new TenantManagementResponse(TenantManagementStatus.CREATED, contents);
  }
}
