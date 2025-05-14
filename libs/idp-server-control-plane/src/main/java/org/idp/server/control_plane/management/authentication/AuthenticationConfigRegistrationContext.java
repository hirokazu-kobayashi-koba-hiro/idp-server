package org.idp.server.control_plane.management.authentication;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.management.authentication.io.AuthenticationConfigManagementResponse;
import org.idp.server.control_plane.management.authentication.io.AuthenticationConfigManagementStatus;
import org.idp.server.core.authentication.AuthenticationConfiguration;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class AuthenticationConfigRegistrationContext {

  Tenant tenant;
  AuthenticationConfiguration authenticationConfiguration;
  boolean dryRun;

  public AuthenticationConfigRegistrationContext(
      Tenant tenant, AuthenticationConfiguration authenticationConfiguration, boolean dryRun) {
    this.tenant = tenant;
    this.authenticationConfiguration = authenticationConfiguration;
    this.dryRun = dryRun;
  }

  public Tenant tenant() {
    return tenant;
  }

  public AuthenticationConfiguration configuration() {
    return authenticationConfiguration;
  }

  public boolean isDryRun() {
    return dryRun;
  }

  public AuthenticationConfigManagementResponse toResponse() {
    Map<String, Object> response = new HashMap<>();
    response.put("result", authenticationConfiguration.payload());
    response.put("dry_run", dryRun);
    return new AuthenticationConfigManagementResponse(
        AuthenticationConfigManagementStatus.CREATED, response);
  }
}
