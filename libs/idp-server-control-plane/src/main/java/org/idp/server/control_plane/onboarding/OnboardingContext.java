package org.idp.server.control_plane.onboarding;

import java.util.Map;
import org.idp.server.control_plane.onboarding.io.OnboardingResponse;
import org.idp.server.control_plane.onboarding.io.OnboardingStatus;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.permission.Permissions;
import org.idp.server.core.identity.role.Roles;
import org.idp.server.core.multi_tenancy.organization.Organization;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;

public class OnboardingContext {

  Tenant tenant;
  AuthorizationServerConfiguration authorizationServerConfiguration;
  Organization organization;
  Permissions permissions;
  Roles roles;
  User user;

  public OnboardingContext(
      Tenant tenant,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      Organization organization,
      Permissions permissions,
      Roles roles,
      User user) {
    this.tenant = tenant;
    this.authorizationServerConfiguration = authorizationServerConfiguration;
    this.organization = organization;
    this.permissions = permissions;
    this.roles = roles;
    this.user = user;
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

  public OnboardingResponse toResponse() {
    Map<String, Object> contents =
        Map.of("organization", organization.toMap(), "tenant", tenant.toMap());
    return new OnboardingResponse(OnboardingStatus.OK, contents);
  }
}
