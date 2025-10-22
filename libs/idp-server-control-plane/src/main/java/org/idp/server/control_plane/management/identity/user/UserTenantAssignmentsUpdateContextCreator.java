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
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

public class UserTenantAssignmentsUpdateContextCreator {

  Tenant tenant;
  User operator;
  OAuthToken oAuthToken;
  RequestAttributes requestAttributes;
  User before;
  UserRegistrationRequest request;
  boolean dryRun;
  PasswordEncodeDelegation passwordEncodeDelegation;
  JsonConverter jsonConverter;

  public UserTenantAssignmentsUpdateContextCreator(
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
    User updated = before;

    // Update assigned tenants if provided
    if (request.containsKey("assigned_tenants")) {
      updated = updated.setAssignedTenants(request.assignedTenants());
    }

    // Update current tenant if provided
    if (request.containsKey("current_tenant_id") && request.currentTenant() != null) {
      updated =
          updated.setCurrentTenantId(
              new org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier(
                  request.currentTenant()));
    }

    return new UserUpdateContext(
        tenant, operator, oAuthToken, requestAttributes, before, updated, request.toMap(), dryRun);
  }
}
