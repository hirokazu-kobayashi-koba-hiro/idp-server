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

import org.idp.server.control_plane.management.authentication.configuration.io.AuthenticationConfigManagementResponse;
import org.idp.server.control_plane.management.authentication.configuration.io.AuthenticationConfigManagementStatus;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.core.openid.authentication.config.AuthenticationConfiguration;
import org.idp.server.core.openid.authentication.config.AuthenticationConfigurationIdentifier;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for finding a single authentication configuration.
 *
 * <p>Handles authentication configuration retrieval logic.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Query repository for authentication configuration
 *   <li>Validate existence
 *   <li>Build response with configuration data
 * </ul>
 */
public class AuthenticationConfigFindService
    implements AuthenticationConfigManagementService<AuthenticationConfigurationIdentifier> {

  private final AuthenticationConfigurationQueryRepository
      authenticationConfigurationQueryRepository;

  public AuthenticationConfigFindService(
      AuthenticationConfigurationQueryRepository authenticationConfigurationQueryRepository) {
    this.authenticationConfigurationQueryRepository = authenticationConfigurationQueryRepository;
  }

  @Override
  public AuthenticationConfigManagementResult execute(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationConfigurationIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // 1. Query authentication configuration (including disabled ones)
    AuthenticationConfiguration configuration =
        authenticationConfigurationQueryRepository.findWithDisabled(tenant, identifier, true);

    // 2. Check existence
    if (!configuration.exists()) {
      throw new ResourceNotFoundException(
          "Authentication configuration not found: " + identifier.value());
    }

    // 3. Build response
    return AuthenticationConfigManagementResult.success(
        tenant,
        configuration,
        new AuthenticationConfigManagementResponse(
            AuthenticationConfigManagementStatus.OK, configuration.toMap()));
  }
}
