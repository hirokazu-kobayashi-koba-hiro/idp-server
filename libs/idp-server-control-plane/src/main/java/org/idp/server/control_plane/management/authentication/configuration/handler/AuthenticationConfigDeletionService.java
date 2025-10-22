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
import java.util.Map;
import org.idp.server.control_plane.management.authentication.configuration.io.AuthenticationConfigManagementResponse;
import org.idp.server.control_plane.management.authentication.configuration.io.AuthenticationConfigManagementStatus;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.core.openid.authentication.config.AuthenticationConfiguration;
import org.idp.server.core.openid.authentication.config.AuthenticationConfigurationIdentifier;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationCommandRepository;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for deleting authentication configurations.
 *
 * <p>Handles authentication configuration deletion logic.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Retrieve existing configuration
 *   <li>Validate existence
 *   <li>Configuration deletion from repository
 *   <li>Dry-run support
 * </ul>
 */
public class AuthenticationConfigDeletionService
    implements AuthenticationConfigManagementService<AuthenticationConfigurationIdentifier> {

  private final AuthenticationConfigurationQueryRepository
      authenticationConfigurationQueryRepository;
  private final AuthenticationConfigurationCommandRepository
      authenticationConfigurationCommandRepository;

  public AuthenticationConfigDeletionService(
      AuthenticationConfigurationQueryRepository authenticationConfigurationQueryRepository,
      AuthenticationConfigurationCommandRepository authenticationConfigurationCommandRepository) {
    this.authenticationConfigurationQueryRepository = authenticationConfigurationQueryRepository;
    this.authenticationConfigurationCommandRepository =
        authenticationConfigurationCommandRepository;
  }

  @Override
  public AuthenticationConfigManagementResult execute(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationConfigurationIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // 1. Retrieve existing configuration
    AuthenticationConfiguration configuration =
        authenticationConfigurationQueryRepository.findWithDisabled(tenant, identifier, true);

    if (!configuration.exists()) {
      throw new ResourceNotFoundException(
          "Authentication configuration not found: " + identifier.value());
    }

    // 2. Dry-run check
    if (dryRun) {
      Map<String, Object> response = new HashMap<>();
      response.put("message", "Authentication configuration deletion simulated successfully");
      response.put("id", configuration.identifier().value());
      response.put("dry_run", true);
      return AuthenticationConfigManagementResult.success(
          tenant,
          null,
          new AuthenticationConfigManagementResponse(
              AuthenticationConfigManagementStatus.OK, response));
    }

    // 3. Delete configuration
    authenticationConfigurationCommandRepository.delete(tenant, configuration);

    // 4. Return success (NO_CONTENT for actual deletion)
    return AuthenticationConfigManagementResult.success(
        tenant,
        null,
        new AuthenticationConfigManagementResponse(
            AuthenticationConfigManagementStatus.NO_CONTENT, Map.of()));
  }
}
