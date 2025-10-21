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

import org.idp.server.control_plane.management.authentication.configuration.AuthenticationConfigRegistrationContext;
import org.idp.server.control_plane.management.authentication.configuration.AuthenticationConfigRegistrationContextCreator;
import org.idp.server.control_plane.management.authentication.configuration.io.AuthenticationConfigManagementResponse;
import org.idp.server.control_plane.management.authentication.configuration.io.AuthenticationConfigRequest;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationCommandRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for creating authentication configurations.
 *
 * <p>Handles authentication configuration creation logic.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Context creation via Context Creator
 *   <li>Configuration registration in repository
 *   <li>Dry-run support
 * </ul>
 */
public class AuthenticationConfigCreationService
    implements AuthenticationConfigManagementService<AuthenticationConfigRequest> {

  private final AuthenticationConfigurationCommandRepository
      authenticationConfigurationCommandRepository;

  public AuthenticationConfigCreationService(
      AuthenticationConfigurationCommandRepository authenticationConfigurationCommandRepository) {
    this.authenticationConfigurationCommandRepository =
        authenticationConfigurationCommandRepository;
  }

  @Override
  public AuthenticationConfigManagementResult execute(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationConfigRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // 1. Context creation
    AuthenticationConfigRegistrationContextCreator contextCreator =
        new AuthenticationConfigRegistrationContextCreator(tenant, request, dryRun);
    AuthenticationConfigRegistrationContext context = contextCreator.create();

    // 2. Dry-run check
    if (dryRun) {
      AuthenticationConfigManagementResponse response = context.toResponse();
      return AuthenticationConfigManagementResult.success(tenant, context, response);
    }

    // 3. Register configuration
    authenticationConfigurationCommandRepository.register(tenant, context.configuration());

    // 4. Return success
    AuthenticationConfigManagementResponse response = context.toResponse();
    return AuthenticationConfigManagementResult.success(tenant, context, response);
  }
}
