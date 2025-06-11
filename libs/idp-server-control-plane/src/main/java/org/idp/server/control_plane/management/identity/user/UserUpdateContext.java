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

package org.idp.server.control_plane.management.identity.user;

import java.util.Map;
import org.idp.server.control_plane.base.ConfigUpdateContext;
import org.idp.server.control_plane.management.identity.user.io.UserManagementResponse;
import org.idp.server.control_plane.management.identity.user.io.UserManagementStatus;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.platform.json.JsonDiffCalculator;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class UserUpdateContext implements ConfigUpdateContext {

  Tenant tenant;
  User before;
  User after;
  boolean dryRun;

  public UserUpdateContext(Tenant tenant, User before, User after, boolean dryRun) {
    this.tenant = tenant;
    this.before = before;
    this.after = after;
    this.dryRun = dryRun;
  }

  public Tenant tenant() {
    return tenant;
  }

  public User before() {
    return before;
  }

  public User after() {
    return after;
  }

  @Override
  public String type() {
    return "user";
  }

  @Override
  public Map<String, Object> beforePayload() {
    return before.toMaskedValueMap();
  }

  @Override
  public Map<String, Object> afterPayload() {
    return after.toMaskedValueMap();
  }

  @Override
  public boolean isDryRun() {
    return dryRun;
  }

  public UserManagementResponse toResponse() {
    JsonNodeWrapper beforeJson = JsonNodeWrapper.fromMap(before.toMap());
    JsonNodeWrapper afterJson = JsonNodeWrapper.fromMap(after.toMap());
    Map<String, Object> diff = JsonDiffCalculator.deepDiff(beforeJson, afterJson);
    Map<String, Object> contents = Map.of("result", after.toMap(), "diff", diff, "dry_run", dryRun);
    return new UserManagementResponse(UserManagementStatus.OK, contents);
  }
}
