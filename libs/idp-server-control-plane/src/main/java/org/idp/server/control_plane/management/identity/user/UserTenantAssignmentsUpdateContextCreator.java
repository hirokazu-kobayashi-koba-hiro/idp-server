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

import org.idp.server.control_plane.management.identity.user.io.UserRegistrationRequest;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.authentication.PasswordEncodeDelegation;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class UserTenantAssignmentsUpdateContextCreator {

  Tenant tenant;
  User before;
  UserRegistrationRequest request;
  boolean dryRun;
  PasswordEncodeDelegation passwordEncodeDelegation;
  JsonConverter jsonConverter;

  public UserTenantAssignmentsUpdateContextCreator(
      Tenant tenant, User before, UserRegistrationRequest request, boolean dryRun) {
    this.tenant = tenant;
    this.before = before;
    this.request = request;
    this.dryRun = dryRun;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  public UserUpdateContext create() {
    User newUser = jsonConverter.read(request.toMap(), User.class);

    if (newUser.hasAssignedTenants()) {
      User updated = before.setAssignedTenants(newUser.assignedTenants());
      return new UserUpdateContext(tenant, before, updated, dryRun);
    }

    return new UserUpdateContext(tenant, before, newUser, dryRun);
  }
}
