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

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.management.authentication.policy.io.AuthenticationPolicyConfigManagementResponse;
import org.idp.server.control_plane.management.authentication.policy.io.AuthenticationPolicyConfigManagementStatus;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicyConfiguration;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicyConfigurationIdentifier;
import org.idp.server.core.openid.authentication.repository.AuthenticationPolicyConfigurationCommandRepository;
import org.idp.server.core.openid.authentication.repository.AuthenticationPolicyConfigurationQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for deleting authentication policy configurations.
 *
 * <p>Handles authentication policy configuration deletion logic.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Retrieve existing configuration
 *   <li>Existence validation
 *   <li>Configuration deletion from repository
 *   <li>Dry-run support
 * </ul>
 */
public class AuthenticationPolicyConfigDeletionService
    implements AuthenticationPolicyConfigManagementService<
        AuthenticationPolicyConfigurationIdentifier> {

  private final AuthenticationPolicyConfigurationQueryRepository
      authenticationPolicyConfigurationQueryRepository;
  private final AuthenticationPolicyConfigurationCommandRepository
      authenticationPolicyConfigurationCommandRepository;

  public AuthenticationPolicyConfigDeletionService(
      AuthenticationPolicyConfigurationQueryRepository
          authenticationPolicyConfigurationQueryRepository,
      AuthenticationPolicyConfigurationCommandRepository
          authenticationPolicyConfigurationCommandRepository) {
    this.authenticationPolicyConfigurationQueryRepository =
        authenticationPolicyConfigurationQueryRepository;
    this.authenticationPolicyConfigurationCommandRepository =
        authenticationPolicyConfigurationCommandRepository;
  }

  @Override
  public AuthenticationPolicyConfigManagementResult execute(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationPolicyConfigurationIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // 1. Retrieve existing configuration
    AuthenticationPolicyConfiguration configuration =
        authenticationPolicyConfigurationQueryRepository.find(tenant, identifier);

    if (!configuration.exists()) {
      throw new ResourceNotFoundException(
          String.format("Authentication policy configuration %s not found", identifier.value()));
    }

    // 2. Dry-run check
    if (dryRun) {
      Map<String, Object> response = new HashMap<>();
      response.put("message", "Deletion simulated successfully");
      response.put("id", configuration.id());
      response.put("dry_run", true);
      AuthenticationPolicyConfigManagementResponse managementResponse =
          new AuthenticationPolicyConfigManagementResponse(
              AuthenticationPolicyConfigManagementStatus.OK, response);
      return AuthenticationPolicyConfigManagementResult.success(tenant, null, managementResponse);
    }

    // 3. Delete configuration
    authenticationPolicyConfigurationCommandRepository.delete(tenant, configuration);

    // 4. Return success
    AuthenticationPolicyConfigManagementResponse managementResponse =
        new AuthenticationPolicyConfigManagementResponse(
            AuthenticationPolicyConfigManagementStatus.NO_CONTENT, configuration.toMap());
    return AuthenticationPolicyConfigManagementResult.success(tenant, null, managementResponse);
  }
}
