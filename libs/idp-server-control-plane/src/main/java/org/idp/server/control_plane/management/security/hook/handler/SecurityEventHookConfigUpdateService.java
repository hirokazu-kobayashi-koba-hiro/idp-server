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
import org.idp.server.control_plane.management.security.hook.SecurityEventHookConfigUpdateContext;
import org.idp.server.control_plane.management.security.hook.SecurityEventHookConfigUpdateContextCreator;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfiguration;
import org.idp.server.platform.security.repository.SecurityEventHookConfigurationCommandRepository;
import org.idp.server.platform.security.repository.SecurityEventHookConfigurationQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for updating security event hook configurations.
 *
 * <p>Handles security event hook configuration update logic following the Handler/Service pattern.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Existing configuration retrieval and existence verification
 *   <li>Update context creation from request
 *   <li>Dry-run simulation support
 *   <li>Configuration update
 * </ul>
 */
public class SecurityEventHookConfigUpdateService
    implements SecurityEventHookConfigManagementService<SecurityEventHookConfigUpdateRequest> {

  private final SecurityEventHookConfigurationQueryRepository
      securityEventHookConfigurationQueryRepository;
  private final SecurityEventHookConfigurationCommandRepository
      securityEventHookConfigurationCommandRepository;

  public SecurityEventHookConfigUpdateService(
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
      SecurityEventHookConfigUpdateRequest updateRequest,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    SecurityEventHookConfiguration before =
        securityEventHookConfigurationQueryRepository.findWithDisabled(
            tenant, updateRequest.identifier(), true);

    if (!before.exists()) {
      throw new ResourceNotFoundException(
          "Security event hook configuration not found: " + updateRequest.identifier().value());
    }

    SecurityEventHookConfigUpdateContextCreator contextCreator =
        new SecurityEventHookConfigUpdateContextCreator(
            tenant, before, updateRequest.identifier(), updateRequest.request(), dryRun);
    SecurityEventHookConfigUpdateContext context = contextCreator.create();

    if (context.dryRun()) {
      return SecurityEventHookConfigManagementResult.successWithContext(
          tenant, context.toResponse(), context);
    }

    securityEventHookConfigurationCommandRepository.update(tenant, context.afterConfiguration());

    return SecurityEventHookConfigManagementResult.successWithContext(
        tenant, context.toResponse(), context);
  }
}
