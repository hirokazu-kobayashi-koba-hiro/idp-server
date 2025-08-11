/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.control_plane.management.authentication.configuration;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.ConfigUpdateContext;
import org.idp.server.control_plane.management.authentication.configuration.io.AuthenticationConfigManagementResponse;
import org.idp.server.control_plane.management.authentication.configuration.io.AuthenticationConfigManagementStatus;
import org.idp.server.core.oidc.authentication.config.AuthenticationConfiguration;
import org.idp.server.platform.json.JsonDiffCalculator;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class AuthenticationConfigUpdateContext implements ConfigUpdateContext {

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

  @Override
  public String type() {
    return after.type();
  }

  @Override
  public Map<String, Object> beforePayload() {
    return before.toMap();
  }

  @Override
  public Map<String, Object> afterPayload() {
    return after.toMap();
  }

  @Override
  public boolean isDryRun() {
    return dryRun;
  }

  public AuthenticationConfigManagementResponse toResponse() {
    JsonNodeWrapper beforeJson = JsonNodeWrapper.fromMap(before.toMap());
    JsonNodeWrapper afterJson = JsonNodeWrapper.fromMap(after.toMap());
    Map<String, Object> diff = JsonDiffCalculator.deepDiff(beforeJson, afterJson);
    Map<String, Object> response = new HashMap<>();
    response.put("result", after.toMap());
    response.put("diff", diff);
    response.put("dry_run", dryRun);
    return new AuthenticationConfigManagementResponse(
        AuthenticationConfigManagementStatus.OK, response);
  }
}
