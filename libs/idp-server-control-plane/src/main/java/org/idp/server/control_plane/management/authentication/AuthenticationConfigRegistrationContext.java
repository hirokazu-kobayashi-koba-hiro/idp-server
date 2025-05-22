/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.control_plane.management.authentication;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.management.authentication.io.AuthenticationConfigManagementResponse;
import org.idp.server.control_plane.management.authentication.io.AuthenticationConfigManagementStatus;
import org.idp.server.core.oidc.authentication.AuthenticationConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

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
