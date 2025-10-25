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

package org.idp.server.control_plane.management.permission.handler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.control_plane.management.permission.PermissionManagementContextBuilder;
import org.idp.server.control_plane.management.permission.io.PermissionFindListRequest;
import org.idp.server.control_plane.management.permission.io.PermissionManagementResponse;
import org.idp.server.control_plane.management.permission.io.PermissionManagementStatus;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.permission.Permission;
import org.idp.server.core.openid.identity.permission.PermissionQueries;
import org.idp.server.core.openid.identity.permission.PermissionQueryRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for retrieving a list of permissions with pagination.
 *
 * <p>Handles permission list retrieval logic following the Handler/Service pattern.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Paginated permission list retrieval from repository
 *   <li>Response construction with list data, total_count, limit, offset
 * </ul>
 */
public class PermissionFindListService
    implements PermissionManagementService<PermissionFindListRequest> {

  private final PermissionQueryRepository permissionQueryRepository;

  public PermissionFindListService(PermissionQueryRepository permissionQueryRepository) {
    this.permissionQueryRepository = permissionQueryRepository;
  }

  @Override
  public PermissionManagementResponse execute(
      PermissionManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      PermissionFindListRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    PermissionQueries queries = request.queries();
    long totalCount = permissionQueryRepository.findTotalCount(tenant, queries);
    Map<String, Object> response = new HashMap<>();

    if (totalCount == 0) {
      response.put("list", List.of());
      response.put("total_count", 0);
      response.put("limit", queries.limit());
      response.put("offset", queries.offset());
      response.put("dry_run", dryRun);
      return new PermissionManagementResponse(PermissionManagementStatus.OK, response);
    }

    List<Permission> permissions = permissionQueryRepository.findList(tenant, queries);
    response.put("list", permissions.stream().map(Permission::toMap).toList());
    response.put("total_count", totalCount);
    response.put("limit", queries.limit());
    response.put("offset", queries.offset());
    response.put("dry_run", dryRun);

    return new PermissionManagementResponse(PermissionManagementStatus.OK, response);
  }
}
