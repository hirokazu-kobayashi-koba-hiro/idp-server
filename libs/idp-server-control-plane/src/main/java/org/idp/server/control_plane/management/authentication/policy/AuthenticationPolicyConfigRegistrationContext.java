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
import org.idp.server.control_plane.base.ConfigRegistrationContext;
import org.idp.server.control_plane.management.authentication.policy.io.AuthenticationPolicyConfigManagementResponse;
import org.idp.server.control_plane.management.authentication.policy.io.AuthenticationPolicyConfigManagementStatus;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicyConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class AuthenticationPolicyConfigRegistrationContext implements ConfigRegistrationContext {

  Tenant tenant;
  AuthenticationPolicyConfiguration authenticationPolicyConfiguration;
  boolean dryRun;

  public AuthenticationPolicyConfigRegistrationContext(
      Tenant tenant,
      AuthenticationPolicyConfiguration authenticationPolicyConfiguration,
      boolean dryRun) {
    this.tenant = tenant;
    this.authenticationPolicyConfiguration = authenticationPolicyConfiguration;
    this.dryRun = dryRun;
  }

  public Tenant tenant() {
    return tenant;
  }

  public AuthenticationPolicyConfiguration configuration() {
    return authenticationPolicyConfiguration;
  }

  @Override
  public String type() {
    return configuration().flow();
  }

  @Override
  public Map<String, Object> payload() {
    return authenticationPolicyConfiguration.toMap();
  }

  @Override
  public boolean isDryRun() {
    return dryRun;
  }

  public AuthenticationPolicyConfigManagementResponse toResponse() {
    Map<String, Object> response = new HashMap<>();
    response.put("result", authenticationPolicyConfiguration.toMap());
    response.put("dry_run", dryRun);
    return new AuthenticationPolicyConfigManagementResponse(
        AuthenticationPolicyConfigManagementStatus.CREATED, response);
  }
}
