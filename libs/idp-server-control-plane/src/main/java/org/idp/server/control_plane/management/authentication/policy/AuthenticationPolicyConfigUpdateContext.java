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

package org.idp.server.control_plane.management.authentication.policy;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.ConfigUpdateContext;
import org.idp.server.control_plane.management.authentication.policy.io.AuthenticationPolicyConfigManagementResponse;
import org.idp.server.control_plane.management.authentication.policy.io.AuthenticationPolicyConfigManagementStatus;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicyConfiguration;
import org.idp.server.platform.json.JsonDiffCalculator;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class AuthenticationPolicyConfigUpdateContext implements ConfigUpdateContext {

  Tenant tenant;
  AuthenticationPolicyConfiguration before;
  AuthenticationPolicyConfiguration after;
  boolean dryRun;

  public AuthenticationPolicyConfigUpdateContext(
      Tenant tenant,
      AuthenticationPolicyConfiguration before,
      AuthenticationPolicyConfiguration after,
      boolean dryRun) {
    this.tenant = tenant;
    this.before = before;
    this.after = after;
    this.dryRun = dryRun;
  }

  public Tenant tenant() {
    return tenant;
  }

  public AuthenticationPolicyConfiguration before() {
    return before;
  }

  public AuthenticationPolicyConfiguration after() {
    return after;
  }

  @Override
  public String type() {
    return "authentication_policy_config";
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

  public AuthenticationPolicyConfigManagementResponse toResponse() {
    JsonNodeWrapper beforeJson = JsonNodeWrapper.fromMap(before.toMap());
    JsonNodeWrapper afterJson = JsonNodeWrapper.fromMap(after.toMap());
    Map<String, Object> diff = JsonDiffCalculator.deepDiff(beforeJson, afterJson);
    Map<String, Object> response = new HashMap<>();
    response.put("result", after.toMap());
    response.put("diff", diff);
    response.put("dry_run", dryRun);
    return new AuthenticationPolicyConfigManagementResponse(
        AuthenticationPolicyConfigManagementStatus.OK, response);
  }
}
