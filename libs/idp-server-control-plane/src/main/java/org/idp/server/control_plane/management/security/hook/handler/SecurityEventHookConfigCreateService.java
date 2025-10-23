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

import java.util.Map;
import java.util.UUID;
import org.idp.server.control_plane.management.security.hook.SecurityEventHookConfigManagementContextBuilder;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookConfigManagementResponse;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookConfigManagementStatus;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookConfigurationRequest;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookRequest;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfiguration;
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
 *   <li>Configuration creation from request
 *   <li>Builder population (withAfter)
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
  public SecurityEventHookConfigManagementResponse execute(
      SecurityEventHookConfigManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventHookRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // 1. Create configuration from request
    JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
    SecurityEventHookConfigurationRequest configurationRequest =
        jsonConverter.read(request.toMap(), SecurityEventHookConfigurationRequest.class);
    String id =
        configurationRequest.hasId() ? configurationRequest.id() : UUID.randomUUID().toString();
    SecurityEventHookConfiguration configuration = configurationRequest.toConfiguration(id);

    // 2. Populate builder with created configuration
    builder.withAfter(configuration);

    // 3. Build response
    Map<String, Object> contents = Map.of("result", configuration.toMap(), "dry_run", dryRun);

    // 4. Dry-run check
    if (dryRun) {
      return new SecurityEventHookConfigManagementResponse(
          SecurityEventHookConfigManagementStatus.OK, contents);
    }

    // 5. Register configuration
    securityEventHookConfigurationCommandRepository.register(tenant, configuration);

    return new SecurityEventHookConfigManagementResponse(
        SecurityEventHookConfigManagementStatus.CREATED, contents);
  }
}
