package org.idp.server.control_plane.admin.starter;

import java.util.Map;
import org.idp.server.control_plane.admin.starter.io.IdpServerStarterResponse;
import org.idp.server.control_plane.admin.starter.io.IdpServerStarterStatus;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.permission.Permissions;
import org.idp.server.core.identity.role.Roles;
import org.idp.server.core.multi_tenancy.organization.Organization;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;

public class IdpServerStarterContext {

  Tenant tenant;
  AuthorizationServerConfiguration authorizationServerConfiguration;
  Organization organization;
  Permissions permissions;
  Roles roles;
  User user;
  ClientConfiguration clientConfiguration;
  boolean dryRun;

  public IdpServerStarterContext(
      Tenant tenant,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      Organization organization,
      Permissions permissions,
      Roles roles,
      User user,
      ClientConfiguration clientConfiguration,
      boolean dryRun) {
    this.tenant = tenant;
    this.authorizationServerConfiguration = authorizationServerConfiguration;
    this.organization = organization;
    this.permissions = permissions;
    this.roles = roles;
    this.user = user;
    this.clientConfiguration = clientConfiguration;
    this.dryRun = dryRun;
  }

  public Tenant tenant() {
    return tenant;
  }

  public AuthorizationServerConfiguration authorizationServerConfiguration() {
    return authorizationServerConfiguration;
  }

  public Organization organization() {
    return organization;
  }

  public Permissions permissions() {
    return permissions;
  }

  public Roles roles() {
    return roles;
  }

  public User user() {
    return user;
  }

  public ClientConfiguration clientConfiguration() {
    return clientConfiguration;
  }

  public boolean isDryRun() {
    return dryRun;
  }

  public IdpServerStarterResponse toResponse() {
    Map<String, Object> contents =
        Map.of("organization", organization.toMap(), "tenant", tenant.toMap(), "dry_run", dryRun);
    return new IdpServerStarterResponse(IdpServerStarterStatus.OK, contents);
  }
}
