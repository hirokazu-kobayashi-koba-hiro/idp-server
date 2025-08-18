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

import org.idp.server.control_plane.management.authentication.policy.io.AuthenticationPolicyConfigRequest;
import org.idp.server.control_plane.management.authentication.policy.io.AuthenticationPolicyConfigurationRequest;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicyConfiguration;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class AuthenticationPolicyConfigUpdateContextCreator {

  Tenant tenant;
  AuthenticationPolicyConfiguration before;
  AuthenticationPolicyConfigRequest request;
  boolean dryRun;
  JsonConverter jsonConverter;

  public AuthenticationPolicyConfigUpdateContextCreator(
      Tenant tenant,
      AuthenticationPolicyConfiguration before,
      AuthenticationPolicyConfigRequest request,
      boolean dryRun) {
    this.tenant = tenant;
    this.before = before;
    this.request = request;
    this.dryRun = dryRun;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  public AuthenticationPolicyConfigUpdateContext create() {
    AuthenticationPolicyConfigurationRequest configurationRequest =
        jsonConverter.read(request.toMap(), AuthenticationPolicyConfigurationRequest.class);

    AuthenticationPolicyConfiguration after = configurationRequest.toConfiguration(before.id());

    return new AuthenticationPolicyConfigUpdateContext(tenant, before, after, dryRun);
  }
}
