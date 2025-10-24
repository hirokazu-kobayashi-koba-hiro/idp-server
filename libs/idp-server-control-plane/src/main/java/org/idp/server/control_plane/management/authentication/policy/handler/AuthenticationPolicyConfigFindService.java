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

import org.idp.server.control_plane.management.authentication.policy.AuthenticationPolicyConfigManagementContextBuilder;
import org.idp.server.control_plane.management.authentication.policy.io.AuthenticationPolicyConfigFindRequest;
import org.idp.server.control_plane.management.authentication.policy.io.AuthenticationPolicyConfigManagementResponse;
import org.idp.server.control_plane.management.authentication.policy.io.AuthenticationPolicyConfigManagementStatus;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicyConfiguration;
import org.idp.server.core.openid.authentication.repository.AuthenticationPolicyConfigurationQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for authentication policy configuration retrieval operations.
 *
 * <p>Handles business logic for retrieving a single authentication policy configuration. Part of
 * Handler/Service pattern.
 */
public class AuthenticationPolicyConfigFindService
    implements AuthenticationPolicyConfigManagementService<AuthenticationPolicyConfigFindRequest> {

  private final AuthenticationPolicyConfigurationQueryRepository queryRepository;

  public AuthenticationPolicyConfigFindService(
      AuthenticationPolicyConfigurationQueryRepository queryRepository) {
    this.queryRepository = queryRepository;
  }

  @Override
  public AuthenticationPolicyConfigManagementResponse execute(
      AuthenticationPolicyConfigManagementContextBuilder contextBuilder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationPolicyConfigFindRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AuthenticationPolicyConfiguration configuration =
        queryRepository.findWithDisabled(tenant, request.identifier(), true);

    if (!configuration.exists()) {
      throw new ResourceNotFoundException(
          "Authentication policy configuration not found: " + request.identifier().value());
    }

    // Update context builder with before state (for audit logging)
    contextBuilder.withBefore(configuration);

    return new AuthenticationPolicyConfigManagementResponse(
        AuthenticationPolicyConfigManagementStatus.OK, configuration.toMap());
  }
}
