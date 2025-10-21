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

package org.idp.server.control_plane.management.authentication.policy.handler;

import java.util.List;
import java.util.Map;
import org.idp.server.control_plane.management.authentication.policy.io.AuthenticationPolicyConfigManagementResponse;
import org.idp.server.control_plane.management.authentication.policy.io.AuthenticationPolicyConfigManagementStatus;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicyConfiguration;
import org.idp.server.core.openid.authentication.repository.AuthenticationPolicyConfigurationQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for finding authentication policy configuration list.
 *
 * <p>Handles authentication policy configuration list retrieval logic with pagination support.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Query execution with pagination
 *   <li>Total count retrieval
 *   <li>Response formatting
 * </ul>
 */
public class AuthenticationPolicyConfigFindListService
    implements AuthenticationPolicyConfigManagementService<
        AuthenticationPolicyConfigFindListRequest> {

  private final AuthenticationPolicyConfigurationQueryRepository
      authenticationPolicyConfigurationQueryRepository;

  public AuthenticationPolicyConfigFindListService(
      AuthenticationPolicyConfigurationQueryRepository
          authenticationPolicyConfigurationQueryRepository) {
    this.authenticationPolicyConfigurationQueryRepository =
        authenticationPolicyConfigurationQueryRepository;
  }

  @Override
  public AuthenticationPolicyConfigManagementResult execute(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationPolicyConfigFindListRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    long totalCount = authenticationPolicyConfigurationQueryRepository.findTotalCount(tenant);
    if (totalCount == 0) {
      Map<String, Object> response =
          Map.of(
              "list", List.of(),
              "total_count", 0,
              "limit", request.limit(),
              "offset", request.offset());
      AuthenticationPolicyConfigManagementResponse managementResponse =
          new AuthenticationPolicyConfigManagementResponse(
              AuthenticationPolicyConfigManagementStatus.OK, response);
      return AuthenticationPolicyConfigManagementResult.success(tenant, null, managementResponse);
    }

    List<AuthenticationPolicyConfiguration> configurations =
        authenticationPolicyConfigurationQueryRepository.findList(
            tenant, request.limit(), request.offset());

    Map<String, Object> response =
        Map.of(
            "list", configurations.stream().map(AuthenticationPolicyConfiguration::toMap).toList(),
            "total_count", totalCount,
            "limit", request.limit(),
            "offset", request.offset());

    AuthenticationPolicyConfigManagementResponse managementResponse =
        new AuthenticationPolicyConfigManagementResponse(
            AuthenticationPolicyConfigManagementStatus.OK, response);
    return AuthenticationPolicyConfigManagementResult.success(tenant, null, managementResponse);
  }
}
