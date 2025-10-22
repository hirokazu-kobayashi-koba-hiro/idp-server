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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.control_plane.management.identity.user.UserManagementContextBuilder;
import org.idp.server.control_plane.management.identity.user.io.UserManagementResponse;
import org.idp.server.control_plane.management.identity.user.io.UserManagementStatus;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserQueries;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for finding user lists with pagination.
 *
 * <p>Handles user list retrieval logic following the Handler/Service pattern. This is a read-only
 * operation.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Total count retrieval
 *   <li>Paginated user list retrieval
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
public class UserFindListService implements UserManagementService<UserQueries> {

  private final UserQueryRepository userQueryRepository;

  public UserFindListService(UserQueryRepository userQueryRepository) {
    this.userQueryRepository = userQueryRepository;
  }

  @Override
  public UserManagementResponse execute(
      UserManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      UserQueries queries,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // Cast to specific builder type (UserFindListContextBuilder doesn't need extra data)
    UserFindListContextBuilder findListBuilder = (UserFindListContextBuilder) builder;

    // 1. Get total count
    long totalCount = userQueryRepository.findTotalCount(tenant, queries);
    if (totalCount == 0) {
      Map<String, Object> response = new HashMap<>();
      response.put("list", List.of());
      response.put("total_count", 0);
      response.put("limit", queries.limit());
      response.put("offset", queries.offset());
      return new UserManagementResponse(UserManagementStatus.OK, response);
    }

    // 2. Get user list
    List<User> users = userQueryRepository.findList(tenant, queries);

    // 3. Format response
    Map<String, Object> response = new HashMap<>();
    response.put("list", users.stream().map(User::toMap).toList());
    response.put("total_count", totalCount);
    response.put("limit", queries.limit());
    response.put("offset", queries.offset());

    return new UserManagementResponse(UserManagementStatus.OK, response);
  }

  @Override
  public UserManagementContextBuilder createContextBuilder(
      TenantIdentifier tenantIdentifier,
      OrganizationIdentifier organizationIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RequestAttributes requestAttributes,
      UserQueries request,
      boolean dryRun) {
    return new UserFindListContextBuilder(
            tenantIdentifier,
            organizationIdentifier,
            operator,
            oAuthToken,
            requestAttributes,
            request)
        .withDryRun(dryRun);
  }
}
