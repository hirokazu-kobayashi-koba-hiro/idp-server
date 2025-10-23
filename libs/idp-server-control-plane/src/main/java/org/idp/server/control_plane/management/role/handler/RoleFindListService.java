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

package org.idp.server.control_plane.management.role.handler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.control_plane.management.role.RoleManagementContextBuilder;
import org.idp.server.control_plane.management.role.io.RoleFindListRequest;
import org.idp.server.control_plane.management.role.io.RoleManagementResponse;
import org.idp.server.control_plane.management.role.io.RoleManagementStatus;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.role.Role;
import org.idp.server.core.openid.identity.role.RoleQueries;
import org.idp.server.core.openid.identity.role.RoleQueryRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for retrieving a list of roles with pagination.
 *
 * <p>Handles role list retrieval logic following the Handler/Service pattern.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Paginated role list retrieval from repository
 *   <li>Response construction with list data, total_count, limit, offset
 * </ul>
 */
public class RoleFindListService implements RoleManagementService<RoleFindListRequest> {

  private final RoleQueryRepository roleQueryRepository;

  public RoleFindListService(RoleQueryRepository roleQueryRepository) {
    this.roleQueryRepository = roleQueryRepository;
  }

  @Override
  public RoleManagementResponse execute(
      RoleManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      RoleFindListRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    RoleQueries queries = request.queries();
    long totalCount = roleQueryRepository.findTotalCount(tenant, queries);
    Map<String, Object> response = new HashMap<>();

    if (totalCount == 0) {
      response.put("list", List.of());
      response.put("total_count", 0);
      response.put("limit", queries.limit());
      response.put("offset", queries.offset());
      response.put("dry_run", dryRun);
      return new RoleManagementResponse(RoleManagementStatus.OK, response);
    }

    List<Role> roles = roleQueryRepository.findList(tenant, queries);
    response.put("list", roles.stream().map(Role::toMap).toList());
    response.put("total_count", totalCount);
    response.put("limit", queries.limit());
    response.put("offset", queries.offset());
    response.put("dry_run", dryRun);

    return new RoleManagementResponse(RoleManagementStatus.OK, response);
  }
}
