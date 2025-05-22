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
import org.idp.server.basic.json.JsonDiffCalculator;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.control_plane.management.authentication.io.AuthenticationConfigManagementResponse;
import org.idp.server.control_plane.management.authentication.io.AuthenticationConfigManagementStatus;
import org.idp.server.core.oidc.authentication.AuthenticationConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class AuthenticationConfigUpdateContext {

  Tenant tenant;
  AuthenticationConfiguration before;
  AuthenticationConfiguration after;
  boolean dryRun;

  public AuthenticationConfigUpdateContext(
      Tenant tenant,
      AuthenticationConfiguration before,
      AuthenticationConfiguration after,
      boolean dryRun) {
    this.tenant = tenant;
    this.before = before;
    this.after = after;
    this.dryRun = dryRun;
  }

  public Tenant tenant() {
    return tenant;
  }

  public AuthenticationConfiguration before() {
    return before;
  }

  public AuthenticationConfiguration after() {
    return after;
  }

  public boolean isDryRun() {
    return dryRun;
  }

  public AuthenticationConfigManagementResponse toResponse() {
    JsonNodeWrapper beforeJson = JsonNodeWrapper.fromObject(before.payload());
    JsonNodeWrapper afterJson = JsonNodeWrapper.fromObject(after.payload());
    Map<String, Object> diff = JsonDiffCalculator.deepDiff(beforeJson, afterJson);
    Map<String, Object> response = new HashMap<>();
    response.put("result", after.payload());
    response.put("diff", diff);
    response.put("dry_run", dryRun);
    return new AuthenticationConfigManagementResponse(
        AuthenticationConfigManagementStatus.OK, response);
  }
}
