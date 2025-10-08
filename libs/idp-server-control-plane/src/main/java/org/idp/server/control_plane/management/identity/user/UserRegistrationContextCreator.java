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

import java.util.UUID;
import org.idp.server.control_plane.management.identity.user.io.UserRegistrationRequest;
import org.idp.server.core.openid.identity.TenantIdentityPolicy;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserStatus;
import org.idp.server.core.openid.identity.authentication.PasswordEncodeDelegation;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class UserRegistrationContextCreator {

  Tenant tenant;
  UserRegistrationRequest request;
  boolean dryRun;
  PasswordEncodeDelegation passwordEncodeDelegation;
  JsonConverter jsonConverter;

  public UserRegistrationContextCreator(
      Tenant tenant,
      UserRegistrationRequest request,
      boolean dryRun,
      PasswordEncodeDelegation passwordEncodeDelegation) {
    this.tenant = tenant;
    this.request = request;
    this.dryRun = dryRun;
    this.passwordEncodeDelegation = passwordEncodeDelegation;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  public UserRegistrationContext create() {
    User user = jsonConverter.read(request.toMap(), User.class);
    if (!user.hasSub()) {
      user.setSub(UUID.randomUUID().toString());
    }

    // Apply tenant identity policy to set preferred_username if not set
    if (user.preferredUsername() == null || user.preferredUsername().isBlank()) {
      TenantIdentityPolicy policy = TenantIdentityPolicy.fromTenantAttributes(tenant.attributes());
      user.applyIdentityPolicy(policy);
    }

    String encoded = passwordEncodeDelegation.encode(user.rawPassword());
    user.setHashedPassword(encoded);
    user.setStatus(UserStatus.REGISTERED);

    return new UserRegistrationContext(tenant, user, request, dryRun);
  }
}
