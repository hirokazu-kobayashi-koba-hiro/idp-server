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
import org.idp.server.control_plane.management.authentication.policy.AuthenticationPolicyConfigManagementContextBuilder;
import org.idp.server.control_plane.management.authentication.policy.io.AuthenticationPolicyConfigFindListRequest;
import org.idp.server.control_plane.management.authentication.policy.io.AuthenticationPolicyConfigManagementResponse;
import org.idp.server.control_plane.management.authentication.policy.io.AuthenticationPolicyConfigManagementStatus;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicyConfiguration;
import org.idp.server.core.openid.authentication.repository.AuthenticationPolicyConfigurationQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for authentication policy configuration list retrieval operations.
 *
 * <p>Handles business logic for retrieving lists of authentication policy configurations. Part of
 * Handler/Service pattern.
 */
public class AuthenticationPolicyConfigFindListService
    implements AuthenticationPolicyConfigManagementService<
        AuthenticationPolicyConfigFindListRequest> {

  private final AuthenticationPolicyConfigurationQueryRepository queryRepository;

  public AuthenticationPolicyConfigFindListService(
      AuthenticationPolicyConfigurationQueryRepository queryRepository) {
    this.queryRepository = queryRepository;
  }

  @Override
  public AuthenticationPolicyConfigManagementResponse execute(
      AuthenticationPolicyConfigManagementContextBuilder contextBuilder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationPolicyConfigFindListRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    long totalCount = queryRepository.findTotalCount(tenant);
    if (totalCount == 0) {
      Map<String, Object> response =
          Map.of(
              "list",
              List.of(),
              "total_count",
              totalCount,
              "limit",
              request.limit(),
              "offset",
              request.offset());
      return new AuthenticationPolicyConfigManagementResponse(
          AuthenticationPolicyConfigManagementStatus.OK, response);
    }

    List<AuthenticationPolicyConfiguration> configurations =
        queryRepository.findList(tenant, request.limit(), request.offset());

    Map<String, Object> response =
        Map.of(
            "list",
            configurations.stream().map(AuthenticationPolicyConfiguration::toMap).toList(),
            "total_count",
            totalCount,
            "limit",
            request.limit(),
            "offset",
            request.offset());

    return new AuthenticationPolicyConfigManagementResponse(
        AuthenticationPolicyConfigManagementStatus.OK, response);
  }
}
