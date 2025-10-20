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

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookConfigManagementResponse;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookConfigManagementStatus;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfiguration;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfigurationIdentifier;
import org.idp.server.platform.security.repository.SecurityEventHookConfigurationCommandRepository;
import org.idp.server.platform.security.repository.SecurityEventHookConfigurationQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for deleting security event hook configurations.
 *
 * <p>Handles security event hook configuration deletion logic following the Handler/Service
 * pattern.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Existing configuration retrieval and existence verification
 *   <li>Dry-run simulation support
 *   <li>Configuration deletion
 * </ul>
 */
public class SecurityEventHookConfigDeleteService
    implements SecurityEventHookConfigManagementService<SecurityEventHookConfigurationIdentifier> {

  private final SecurityEventHookConfigurationQueryRepository
      securityEventHookConfigurationQueryRepository;
  private final SecurityEventHookConfigurationCommandRepository
      securityEventHookConfigurationCommandRepository;

  public SecurityEventHookConfigDeleteService(
      SecurityEventHookConfigurationQueryRepository securityEventHookConfigurationQueryRepository,
      SecurityEventHookConfigurationCommandRepository
          securityEventHookConfigurationCommandRepository) {
    this.securityEventHookConfigurationQueryRepository =
        securityEventHookConfigurationQueryRepository;
    this.securityEventHookConfigurationCommandRepository =
        securityEventHookConfigurationCommandRepository;
  }

  @Override
  public SecurityEventHookConfigManagementResult execute(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventHookConfigurationIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    SecurityEventHookConfiguration configuration =
        securityEventHookConfigurationQueryRepository.findWithDisabled(tenant, identifier, true);

    if (!configuration.exists()) {
      throw new ResourceNotFoundException(
          "Security event hook configuration not found: " + identifier.value());
    }

    if (dryRun) {
      Map<String, Object> response = new HashMap<>();
      response.put("message", "Deletion simulated successfully");
      response.put("id", configuration.identifier().value());
      response.put("dry_run", true);
      return SecurityEventHookConfigManagementResult.successWithContext(
          tenant,
          new SecurityEventHookConfigManagementResponse(
              SecurityEventHookConfigManagementStatus.OK, response),
          configuration.toMap());
    }

    securityEventHookConfigurationCommandRepository.delete(tenant, configuration);

    return SecurityEventHookConfigManagementResult.successWithContext(
        tenant,
        new SecurityEventHookConfigManagementResponse(
            SecurityEventHookConfigManagementStatus.NO_CONTENT, null),
        configuration.toMap());
  }
}
