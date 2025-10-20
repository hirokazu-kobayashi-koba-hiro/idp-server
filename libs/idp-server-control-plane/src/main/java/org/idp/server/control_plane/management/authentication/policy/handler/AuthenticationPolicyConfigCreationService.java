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

import org.idp.server.control_plane.management.authentication.policy.AuthenticationPolicyConfigRegistrationContext;
import org.idp.server.control_plane.management.authentication.policy.AuthenticationPolicyConfigRegistrationContextCreator;
import org.idp.server.control_plane.management.authentication.policy.io.AuthenticationPolicyConfigManagementResponse;
import org.idp.server.control_plane.management.authentication.policy.io.AuthenticationPolicyConfigRequest;
import org.idp.server.core.openid.authentication.repository.AuthenticationPolicyConfigurationCommandRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for creating authentication policy configurations.
 *
 * <p>Handles authentication policy configuration creation logic extracted from
 * AuthenticationPolicyConfigurationManagementEntryService.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Context creation
 *   <li>Configuration registration in repository
 *   <li>Dry-run support
 * </ul>
 */
public class AuthenticationPolicyConfigCreationService
    implements AuthenticationPolicyConfigManagementService<AuthenticationPolicyConfigRequest> {

  private final AuthenticationPolicyConfigurationCommandRepository
      authenticationPolicyConfigurationCommandRepository;

  public AuthenticationPolicyConfigCreationService(
      AuthenticationPolicyConfigurationCommandRepository
          authenticationPolicyConfigurationCommandRepository) {
    this.authenticationPolicyConfigurationCommandRepository =
        authenticationPolicyConfigurationCommandRepository;
  }

  @Override
  public AuthenticationPolicyConfigManagementResult execute(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationPolicyConfigRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // 1. Context creation
    AuthenticationPolicyConfigRegistrationContextCreator contextCreator =
        new AuthenticationPolicyConfigRegistrationContextCreator(tenant, request, dryRun);
    AuthenticationPolicyConfigRegistrationContext context = contextCreator.create();

    // 2. Dry-run check
    if (dryRun) {
      AuthenticationPolicyConfigManagementResponse response = context.toResponse();
      return AuthenticationPolicyConfigManagementResult.success(tenant, context, response);
    }

    // 3. Register configuration
    authenticationPolicyConfigurationCommandRepository.register(tenant, context.configuration());

    // 4. Return success
    AuthenticationPolicyConfigManagementResponse response = context.toResponse();
    return AuthenticationPolicyConfigManagementResult.success(tenant, context, response);
  }
}
