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

package org.idp.server.control_plane.management.security.hook_result.handler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.control_plane.management.security.hook_result.SecurityEventHookManagementContextBuilder;
import org.idp.server.control_plane.management.security.hook_result.io.SecurityEventHookFindListRequest;
import org.idp.server.control_plane.management.security.hook_result.io.SecurityEventHookManagementResponse;
import org.idp.server.control_plane.management.security.hook_result.io.SecurityEventHookManagementStatus;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.hook.SecurityEventHookResult;
import org.idp.server.platform.security.hook.SecurityEventHookResultQueries;
import org.idp.server.platform.security.repository.SecurityEventHookResultQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for retrieving a list of security event hook results with pagination.
 *
 * <p>Handles security event hook result list retrieval logic following the Handler/Service pattern.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Total count calculation with query filters
 *   <li>Paginated result retrieval from repository
 *   <li>Response construction with list and pagination metadata
 * </ul>
 */
public class SecurityEventHookFindListService
    implements SecurityEventHookManagementService<SecurityEventHookFindListRequest> {

  private final SecurityEventHookResultQueryRepository securityEventHookResultQueryRepository;

  public SecurityEventHookFindListService(
      SecurityEventHookResultQueryRepository securityEventHookResultQueryRepository) {
    this.securityEventHookResultQueryRepository = securityEventHookResultQueryRepository;
  }

  @Override
  public SecurityEventHookManagementResponse execute(
      SecurityEventHookManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventHookFindListRequest request,
      RequestAttributes requestAttributes) {

    SecurityEventHookResultQueries queries = request.queries();
    long totalCount = securityEventHookResultQueryRepository.findTotalCount(tenant, queries);

    if (totalCount == 0) {
      Map<String, Object> response = new HashMap<>();
      response.put("list", List.of());
      response.put("total_count", 0);
      response.put("limit", queries.limit());
      response.put("offset", queries.offset());
      return new SecurityEventHookManagementResponse(
          SecurityEventHookManagementStatus.OK, response);
    }

    List<SecurityEventHookResult> results =
        securityEventHookResultQueryRepository.findList(tenant, queries);

    Map<String, Object> response = new HashMap<>();
    response.put("list", results.stream().map(SecurityEventHookResult::toMap).toList());
    response.put("total_count", totalCount);
    response.put("limit", queries.limit());
    response.put("offset", queries.offset());

    return new SecurityEventHookManagementResponse(SecurityEventHookManagementStatus.OK, response);
  }
}
