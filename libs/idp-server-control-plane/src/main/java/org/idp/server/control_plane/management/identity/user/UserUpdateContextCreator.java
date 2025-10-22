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
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.policy.TenantIdentityPolicy;
import org.idp.server.platform.type.RequestAttributes;

public class UserUpdateContextCreator {

  Tenant tenant;
  User operator;
  OAuthToken oAuthToken;
  RequestAttributes requestAttributes;
  User before;
  UserRegistrationRequest request;
  boolean dryRun;
  JsonConverter jsonConverter;

  public UserUpdateContextCreator(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      RequestAttributes requestAttributes,
      User before,
      UserRegistrationRequest request,
      boolean dryRun) {
    this.tenant = tenant;
    this.operator = operator;
    this.oAuthToken = oAuthToken;
    this.requestAttributes = requestAttributes;
    this.before = before;
    this.request = request;
    this.dryRun = dryRun;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  public UserUpdateContext create() {
    User newUser = jsonConverter.read(request.toMap(), User.class);
    newUser.setSub(before.sub());

    // Apply tenant identity policy to set preferred_username if not set
    if (newUser.preferredUsername() == null || newUser.preferredUsername().isBlank()) {
      // First try to preserve existing preferred_username
      if (before.hasPreferredUsername()) {
        newUser.setPreferredUsername(before.preferredUsername());
      } else {
        // If before also doesn't have it, apply policy
        TenantIdentityPolicy policy =
            TenantIdentityPolicy.fromTenantAttributes(tenant.attributes());
        newUser.applyIdentityPolicy(policy);
      }
    }

    return new UserUpdateContext(
        tenant, operator, oAuthToken, requestAttributes, before, newUser, request.toMap(), dryRun);
  }
}
