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

import org.idp.server.control_plane.management.security.hook.SecurityEventHookConfigRegistrationContext;
import org.idp.server.control_plane.management.security.hook.SecurityEventHookConfigRegistrationContextCreator;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookRequest;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.repository.SecurityEventHookConfigurationCommandRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for creating security event hook configurations.
 *
 * <p>Handles security event hook configuration creation logic following the Handler/Service
 * pattern.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Context creation from request
 *   <li>Dry-run simulation support
 *   <li>Configuration registration
 * </ul>
 */
public class SecurityEventHookConfigCreateService
    implements SecurityEventHookConfigManagementService<SecurityEventHookRequest> {

  private final SecurityEventHookConfigurationCommandRepository
      securityEventHookConfigurationCommandRepository;

  public SecurityEventHookConfigCreateService(
      SecurityEventHookConfigurationCommandRepository
          securityEventHookConfigurationCommandRepository) {
    this.securityEventHookConfigurationCommandRepository =
        securityEventHookConfigurationCommandRepository;
  }

  @Override
  public SecurityEventHookConfigManagementResult execute(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventHookRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    SecurityEventHookConfigRegistrationContextCreator contextCreator =
        new SecurityEventHookConfigRegistrationContextCreator(tenant, request, dryRun);
    SecurityEventHookConfigRegistrationContext context = contextCreator.create();

    if (context.isDryRun()) {
      return SecurityEventHookConfigManagementResult.successWithContext(
          tenant, context.toResponse(), context);
    }

    securityEventHookConfigurationCommandRepository.register(tenant, context.configuration());

    return SecurityEventHookConfigManagementResult.successWithContext(
        tenant, context.toResponse(), context);
  }
}
