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

package org.idp.server.control_plane.management.security.hook.handler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.control_plane.management.security.hook.SecurityEventHookConfigManagementContextBuilder;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookConfigManagementResponse;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookConfigManagementStatus;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfiguration;
import org.idp.server.platform.security.repository.SecurityEventHookConfigurationQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for retrieving a list of security event hook configurations with pagination.
 *
 * <p>Handles security event hook configuration list retrieval logic following the Handler/Service
 * pattern.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Paginated configuration list retrieval from repository
 *   <li>Response construction with list data
 * </ul>
 */
public class SecurityEventHookConfigFindListService
    implements SecurityEventHookConfigManagementService<SecurityEventHookConfigFindListRequest> {

  private final SecurityEventHookConfigurationQueryRepository
      securityEventHookConfigurationQueryRepository;

  public SecurityEventHookConfigFindListService(
      SecurityEventHookConfigurationQueryRepository securityEventHookConfigurationQueryRepository) {
    this.securityEventHookConfigurationQueryRepository =
        securityEventHookConfigurationQueryRepository;
  }

  @Override
  public SecurityEventHookConfigManagementResponse execute(
      SecurityEventHookConfigManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventHookConfigFindListRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    long totalCount = securityEventHookConfigurationQueryRepository.findTotalCount(tenant);
    if (totalCount == 0) {
      Map<String, Object> response = new HashMap<>();
      response.put("list", List.of());
      response.put("total_count", 0);
      response.put("limit", request.limit());
      response.put("offset", request.offset());
      return new SecurityEventHookConfigManagementResponse(
          SecurityEventHookConfigManagementStatus.OK, response);
    }

    List<SecurityEventHookConfiguration> configurations =
        securityEventHookConfigurationQueryRepository.findList(
            tenant, request.limit(), request.offset());

    Map<String, Object> response = new HashMap<>();
    response.put(
        "list", configurations.stream().map(SecurityEventHookConfiguration::toMap).toList());
    response.put("total_count", totalCount);
    response.put("limit", request.limit());
    response.put("offset", request.offset());

    return new SecurityEventHookConfigManagementResponse(
        SecurityEventHookConfigManagementStatus.OK, response);
  }
}
