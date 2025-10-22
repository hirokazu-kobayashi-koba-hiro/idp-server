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

import org.idp.server.control_plane.management.authentication.configuration.AuthenticationConfigUpdateContext;
import org.idp.server.control_plane.management.authentication.configuration.AuthenticationConfigUpdateContextCreator;
import org.idp.server.control_plane.management.authentication.configuration.io.AuthenticationConfigManagementResponse;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.core.openid.authentication.config.AuthenticationConfiguration;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationCommandRepository;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for updating authentication configurations.
 *
 * <p>Handles authentication configuration update logic.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Retrieve existing configuration
 *   <li>Context creation via Context Creator
 *   <li>Configuration update in repository
 *   <li>Dry-run support
 * </ul>
 */
public class AuthenticationConfigUpdateService
    implements AuthenticationConfigManagementService<AuthenticationConfigUpdateRequest> {

  private final AuthenticationConfigurationQueryRepository
      authenticationConfigurationQueryRepository;
  private final AuthenticationConfigurationCommandRepository
      authenticationConfigurationCommandRepository;

  public AuthenticationConfigUpdateService(
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
      AuthenticationConfigUpdateRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // 1. Retrieve existing configuration
    AuthenticationConfiguration before =
        authenticationConfigurationQueryRepository.findWithDisabled(
            tenant, request.identifier(), true);

    if (!before.exists()) {
      throw new ResourceNotFoundException(
          "Authentication configuration not found: " + request.identifier().value());
    }

    // 2. Context creation
    AuthenticationConfigUpdateContextCreator contextCreator =
        new AuthenticationConfigUpdateContextCreator(tenant, before, request.request(), dryRun);
    AuthenticationConfigUpdateContext context = contextCreator.create();

    // 3. Dry-run check
    if (dryRun) {
      AuthenticationConfigManagementResponse response = context.toResponse();
      return AuthenticationConfigManagementResult.success(tenant, context, response);
    }

    // 4. Update configuration
    authenticationConfigurationCommandRepository.update(tenant, context.afterConfiguration());

    // 5. Return success
    AuthenticationConfigManagementResponse response = context.toResponse();
    return AuthenticationConfigManagementResult.success(tenant, context, response);
  }
}
