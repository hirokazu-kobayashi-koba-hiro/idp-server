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

import java.util.Map;
import org.idp.server.control_plane.management.authentication.policy.AuthenticationPolicyConfigManagementContextBuilder;
import org.idp.server.control_plane.management.authentication.policy.io.AuthenticationPolicyConfigDeleteRequest;
import org.idp.server.control_plane.management.authentication.policy.io.AuthenticationPolicyConfigManagementResponse;
import org.idp.server.control_plane.management.authentication.policy.io.AuthenticationPolicyConfigManagementStatus;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicyConfiguration;
import org.idp.server.core.openid.authentication.repository.AuthenticationPolicyConfigurationCommandRepository;
import org.idp.server.core.openid.authentication.repository.AuthenticationPolicyConfigurationQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for authentication policy configuration deletion operations.
 *
 * <p>Handles business logic for deleting authentication policy configurations. Part of
 * Handler/Service pattern.
 */
public class AuthenticationPolicyConfigDeletionService
    implements AuthenticationPolicyConfigManagementService<
        AuthenticationPolicyConfigDeleteRequest> {

  private final AuthenticationPolicyConfigurationQueryRepository queryRepository;
  private final AuthenticationPolicyConfigurationCommandRepository commandRepository;

  public AuthenticationPolicyConfigDeletionService(
      AuthenticationPolicyConfigurationQueryRepository queryRepository,
      AuthenticationPolicyConfigurationCommandRepository commandRepository) {
    this.queryRepository = queryRepository;
    this.commandRepository = commandRepository;
  }

  @Override
  public AuthenticationPolicyConfigManagementResponse execute(
      AuthenticationPolicyConfigManagementContextBuilder contextBuilder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationPolicyConfigDeleteRequest request,
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

    if (dryRun) {
      Map<String, Object> response =
          Map.of(
              "message",
              "Deletion simulated successfully",
              "id",
              configuration.id(),
              "dry_run",
              true);
      return new AuthenticationPolicyConfigManagementResponse(
          AuthenticationPolicyConfigManagementStatus.OK, response);
    }

    commandRepository.delete(tenant, configuration);

    return new AuthenticationPolicyConfigManagementResponse(
        AuthenticationPolicyConfigManagementStatus.NO_CONTENT, Map.of());
  }
}
