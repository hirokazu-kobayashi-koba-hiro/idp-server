/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.control_plane.management.security.hook;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookConfigManagementResponse;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookConfigManagementStatus;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.hook.SecurityEventHookConfiguration;

public class SecurityEventHookConfigRegistrationContext {

  Tenant tenant;
  SecurityEventHookConfiguration securityEventHookConfiguration;
  boolean dryRun;

  public SecurityEventHookConfigRegistrationContext(
      Tenant tenant,
      SecurityEventHookConfiguration securityEventHookConfiguration,
      boolean dryRun) {
    this.tenant = tenant;
    this.securityEventHookConfiguration = securityEventHookConfiguration;
    this.dryRun = dryRun;
  }

  public Tenant tenant() {
    return tenant;
  }

  public SecurityEventHookConfiguration configuration() {
    return securityEventHookConfiguration;
  }

  public boolean isDryRun() {
    return dryRun;
  }

  public SecurityEventHookConfigManagementResponse toResponse() {
    Map<String, Object> response = new HashMap<>();
    response.put("result", securityEventHookConfiguration.payload());
    response.put("dry_run", dryRun);
    return new SecurityEventHookConfigManagementResponse(
        SecurityEventHookConfigManagementStatus.CREATED, response);
  }
}
