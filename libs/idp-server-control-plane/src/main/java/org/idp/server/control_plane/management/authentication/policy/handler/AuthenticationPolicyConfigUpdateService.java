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

import org.idp.server.control_plane.management.authentication.policy.AuthenticationPolicyConfigUpdateContext;
import org.idp.server.control_plane.management.authentication.policy.AuthenticationPolicyConfigUpdateContextCreator;
import org.idp.server.control_plane.management.authentication.policy.io.AuthenticationPolicyConfigManagementResponse;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicyConfiguration;
import org.idp.server.core.openid.authentication.repository.AuthenticationPolicyConfigurationCommandRepository;
import org.idp.server.core.openid.authentication.repository.AuthenticationPolicyConfigurationQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for updating authentication policy configurations.
 *
 * <p>Handles authentication policy configuration update logic.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Retrieve existing configuration
 *   <li>Context creation
 *   <li>Configuration update in repository
 *   <li>Dry-run support
 * </ul>
 */
public class AuthenticationPolicyConfigUpdateService
    implements AuthenticationPolicyConfigManagementService<
        AuthenticationPolicyConfigUpdateRequest> {

  private final AuthenticationPolicyConfigurationQueryRepository
      authenticationPolicyConfigurationQueryRepository;
  private final AuthenticationPolicyConfigurationCommandRepository
      authenticationPolicyConfigurationCommandRepository;

  public AuthenticationPolicyConfigUpdateService(
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
      AuthenticationPolicyConfigUpdateRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // 1. Retrieve existing configuration
    AuthenticationPolicyConfiguration before =
        authenticationPolicyConfigurationQueryRepository.find(tenant, request.identifier());

    if (!before.exists()) {
      throw new ResourceNotFoundException(
          "Authentication policy configuration not found: " + request.identifier().value());
    }

    // 2. Context creation
    AuthenticationPolicyConfigUpdateContextCreator contextCreator =
        new AuthenticationPolicyConfigUpdateContextCreator(
            tenant, before, request.request(), dryRun);
    AuthenticationPolicyConfigUpdateContext context = contextCreator.create();

    // 3. Dry-run check
    if (dryRun) {
      AuthenticationPolicyConfigManagementResponse response = context.toResponse();
      return AuthenticationPolicyConfigManagementResult.success(tenant, context, response);
    }

    // 4. Update configuration
    authenticationPolicyConfigurationCommandRepository.update(tenant, context.afterConfiguration());

    // 5. Return success
    AuthenticationPolicyConfigManagementResponse response = context.toResponse();
    return AuthenticationPolicyConfigManagementResult.success(tenant, context, response);
  }
}
