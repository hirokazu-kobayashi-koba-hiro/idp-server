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
import org.idp.server.control_plane.management.security.hook.SecurityEventHookConfigManagementContextBuilder;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookConfigManagementResponse;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookConfigManagementStatus;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookConfigurationRequest;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonDiffCalculator;
import org.idp.server.platform.json.JsonNodeWrapper;
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
 *   <li>Updated configuration creation from request
 *   <li>Builder population (withBefore + withAfter)
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
  public SecurityEventHookConfigManagementResponse execute(
      SecurityEventHookConfigManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventHookConfigUpdateRequest updateRequest,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // 1. Retrieve existing configuration
    SecurityEventHookConfiguration before =
        securityEventHookConfigurationQueryRepository.findWithDisabled(
            tenant, updateRequest.identifier(), true);

    if (!before.exists()) {
      throw new ResourceNotFoundException(
          "Security event hook configuration not found: " + updateRequest.identifier().value());
    }

    // 2. Create updated configuration from request
    JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
    SecurityEventHookConfigurationRequest configurationRequest =
        jsonConverter.read(
            updateRequest.request().toMap(), SecurityEventHookConfigurationRequest.class);
    SecurityEventHookConfiguration after =
        configurationRequest.toConfiguration(updateRequest.identifier().value());

    // 3. Populate builder with before/after
    builder.withBefore(before).withAfter(after);

    // 4. Build response
    Map<String, Object> contents = new HashMap<>();
    JsonNodeWrapper beforeJson = JsonNodeWrapper.fromMap(before.toMap());
    JsonNodeWrapper afterJson = JsonNodeWrapper.fromMap(after.toMap());
    contents.put("result", after.toMap());
    contents.put("diff", JsonDiffCalculator.deepDiff(beforeJson, afterJson));
    contents.put("dry_run", dryRun);

    // 5. Dry-run check
    if (dryRun) {
      return new SecurityEventHookConfigManagementResponse(
          SecurityEventHookConfigManagementStatus.OK, contents);
    }

    // 6. Update configuration
    securityEventHookConfigurationCommandRepository.update(tenant, after);

    return new SecurityEventHookConfigManagementResponse(
        SecurityEventHookConfigManagementStatus.OK, contents);
  }
}
