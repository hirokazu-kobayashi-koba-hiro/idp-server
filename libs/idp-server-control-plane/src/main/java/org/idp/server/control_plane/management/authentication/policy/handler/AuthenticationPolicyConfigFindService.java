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

import org.idp.server.control_plane.management.authentication.policy.io.AuthenticationPolicyConfigManagementResponse;
import org.idp.server.control_plane.management.authentication.policy.io.AuthenticationPolicyConfigManagementStatus;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicyConfiguration;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicyConfigurationIdentifier;
import org.idp.server.core.openid.authentication.repository.AuthenticationPolicyConfigurationQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for finding a single authentication policy configuration.
 *
 * <p>Handles authentication policy configuration retrieval by identifier. Throws
 * ResourceNotFoundException if the configuration does not exist.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Configuration retrieval by identifier
 *   <li>Existence validation
 *   <li>Response formatting
 * </ul>
 */
public class AuthenticationPolicyConfigFindService
    implements AuthenticationPolicyConfigManagementService<
        AuthenticationPolicyConfigurationIdentifier> {

  private final AuthenticationPolicyConfigurationQueryRepository
      authenticationPolicyConfigurationQueryRepository;

  public AuthenticationPolicyConfigFindService(
      AuthenticationPolicyConfigurationQueryRepository
          authenticationPolicyConfigurationQueryRepository) {
    this.authenticationPolicyConfigurationQueryRepository =
        authenticationPolicyConfigurationQueryRepository;
  }

  @Override
  public AuthenticationPolicyConfigManagementResult execute(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationPolicyConfigurationIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AuthenticationPolicyConfiguration configuration =
        authenticationPolicyConfigurationQueryRepository.find(tenant, identifier);

    if (!configuration.exists()) {
      throw new ResourceNotFoundException(
          String.format("Authentication policy configuration %s not found", identifier.value()));
    }

    AuthenticationPolicyConfigManagementResponse managementResponse =
        new AuthenticationPolicyConfigManagementResponse(
            AuthenticationPolicyConfigManagementStatus.OK, configuration.toMap());
    return AuthenticationPolicyConfigManagementResult.success(tenant, null, managementResponse);
  }
}
