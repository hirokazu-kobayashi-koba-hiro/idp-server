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

package org.idp.server.control_plane.management.identity.user.handler;

import org.idp.server.control_plane.management.identity.user.io.UserManagementResponse;
import org.idp.server.control_plane.management.identity.user.io.UserManagementStatus;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for finding a single user by identifier.
 *
 * <p>Handles single user retrieval logic following the Handler/Service pattern. This is a read-only
 * operation.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>User existence verification
 *   <li>User data retrieval
 *   <li>Response formatting
 * </ul>
 *
 * <h2>NOT Responsibilities (handled by UserManagementHandler)</h2>
 *
 * <ul>
 *   <li>Permission checking
 *   <li>Audit logging (done in EntryService)
 *   <li>Transaction management
 * </ul>
 */
public class UserFindService implements UserManagementService<UserIdentifier> {

  private final UserQueryRepository userQueryRepository;

  public UserFindService(UserQueryRepository userQueryRepository) {
    this.userQueryRepository = userQueryRepository;
  }

  @Override
  public UserManagementResult execute(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      UserIdentifier userIdentifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // 1. User existence verification
    User user = userQueryRepository.get(tenant, userIdentifier);

    // 2. Return user data
    return UserManagementResult.success(
        tenant, user, new UserManagementResponse(UserManagementStatus.OK, user.toMap()));
  }
}
