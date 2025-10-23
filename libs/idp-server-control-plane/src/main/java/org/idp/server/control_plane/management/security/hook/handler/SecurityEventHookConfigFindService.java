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

package org.idp.server.control_plane.management.security.hook.handler;

import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.security.hook.SecurityEventHookConfigManagementContextBuilder;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookConfigFindRequest;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookConfigManagementResponse;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookConfigManagementStatus;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfiguration;
import org.idp.server.platform.security.repository.SecurityEventHookConfigurationQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for retrieving a single security event hook configuration.
 *
 * <p>Handles security event hook configuration retrieval logic following the Handler/Service
 * pattern.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Security event hook configuration existence verification
 *   <li>Security event hook configuration retrieval from repository
 * </ul>
 */
public class SecurityEventHookConfigFindService
    implements SecurityEventHookConfigManagementService<SecurityEventHookConfigFindRequest> {

  private final SecurityEventHookConfigurationQueryRepository
      securityEventHookConfigurationQueryRepository;

  public SecurityEventHookConfigFindService(
      SecurityEventHookConfigurationQueryRepository securityEventHookConfigurationQueryRepository) {
    this.securityEventHookConfigurationQueryRepository =
        securityEventHookConfigurationQueryRepository;
  }

  @Override
  public SecurityEventHookConfigManagementResponse execute(
      SecurityEventHookConfigManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventHookConfigFindRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    SecurityEventHookConfiguration configuration =
        securityEventHookConfigurationQueryRepository.findWithDisabled(
            tenant, request.identifier(), true);

    if (!configuration.exists()) {
      throw new ResourceNotFoundException(
          "Security event hook configuration not found: " + request.identifier().value());
    }

    // Populate builder with found configuration (for audit logging)
    builder.withBefore(configuration);

    return new SecurityEventHookConfigManagementResponse(
        SecurityEventHookConfigManagementStatus.OK, configuration.toMap());
  }
}
