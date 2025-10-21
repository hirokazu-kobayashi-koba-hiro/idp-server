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

package org.idp.server.control_plane.management.authentication.configuration.handler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.control_plane.management.authentication.configuration.io.AuthenticationConfigManagementResponse;
import org.idp.server.control_plane.management.authentication.configuration.io.AuthenticationConfigManagementStatus;
import org.idp.server.core.openid.authentication.config.AuthenticationConfiguration;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for finding list of authentication configurations.
 *
 * <p>Handles authentication configuration list retrieval logic with pagination support.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Query repository for total count
 *   <li>Query repository for authentication configuration list
 *   <li>Build paginated response
 * </ul>
 */
public class AuthenticationConfigFindListService
    implements AuthenticationConfigManagementService<AuthenticationConfigFindListRequest> {

  private final AuthenticationConfigurationQueryRepository
      authenticationConfigurationQueryRepository;

  public AuthenticationConfigFindListService(
      AuthenticationConfigurationQueryRepository authenticationConfigurationQueryRepository) {
    this.authenticationConfigurationQueryRepository = authenticationConfigurationQueryRepository;
  }

  @Override
  public AuthenticationConfigManagementResult execute(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationConfigFindListRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // 1. Get total count
    long totalCount = authenticationConfigurationQueryRepository.findTotalCount(tenant);

    // 2. Handle empty result
    if (totalCount == 0) {
      Map<String, Object> response = new HashMap<>();
      response.put("list", List.of());
      response.put("total_count", 0);
      response.put("limit", request.limit());
      response.put("offset", request.offset());
      return AuthenticationConfigManagementResult.success(
          tenant,
          request,
          new AuthenticationConfigManagementResponse(
              AuthenticationConfigManagementStatus.OK, response));
    }

    // 3. Query list
    List<AuthenticationConfiguration> configurations =
        authenticationConfigurationQueryRepository.findList(
            tenant, request.limit(), request.offset());

    // 4. Build response
    Map<String, Object> response = new HashMap<>();
    response.put("list", configurations.stream().map(AuthenticationConfiguration::toMap).toList());
    response.put("total_count", totalCount);
    response.put("limit", request.limit());
    response.put("offset", request.offset());

    return AuthenticationConfigManagementResult.success(
        tenant,
        request,
        new AuthenticationConfigManagementResponse(
            AuthenticationConfigManagementStatus.OK, response));
  }
}
