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
import org.idp.server.control_plane.management.identity.user.io.UserManagementResponse;
import org.idp.server.control_plane.management.identity.user.io.UserManagementStatus;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class UserRegistrationContext {

  Tenant tenant;
  User user;
  boolean dryRun;

  public UserRegistrationContext(Tenant tenant, User user, boolean dryRun) {
    this.tenant = tenant;
    this.user = user;
    this.dryRun = dryRun;
  }

  public Tenant tenant() {
    return tenant;
  }

  public User user() {
    return user;
  }

  public boolean isDryRun() {
    return dryRun;
  }

  public UserManagementResponse toResponse() {
    Map<String, Object> contents = Map.of("result", user.toMap(), "dry_run", dryRun);
    return new UserManagementResponse(UserManagementStatus.CREATED, contents);
  }
}
