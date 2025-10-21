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
 * Service for finding permission lists.
 *
 * <p>Handles permission list retrieval with pagination and filtering.
 */
public class PermissionFindListService implements PermissionManagementService<PermissionQueries> {

  private final PermissionQueryRepository permissionQueryRepository;

  public PermissionFindListService(PermissionQueryRepository permissionQueryRepository) {
    this.permissionQueryRepository = permissionQueryRepository;
  }

  @Override
  public PermissionManagementResult execute(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      PermissionQueries queries,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    long totalCount = permissionQueryRepository.findTotalCount(tenant, queries);
    if (totalCount == 0) {
      Map<String, Object> response = new HashMap<>();
      response.put("list", List.of());
      response.put("total_count", 0);
      response.put("limit", queries.limit());
      response.put("offset", queries.offset());
      return PermissionManagementResult.success(
          tenant, new PermissionManagementResponse(PermissionManagementStatus.OK, response));
    }

    List<Permission> permissionList = permissionQueryRepository.findList(tenant, queries);
    Map<String, Object> response = new HashMap<>();
    response.put("list", permissionList.stream().map(Permission::toMap).toList());
    response.put("total_count", totalCount);
    response.put("limit", queries.limit());
    response.put("offset", queries.offset());

    return PermissionManagementResult.success(
        tenant, new PermissionManagementResponse(PermissionManagementStatus.OK, response));
  }
}
