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

package org.idp.server.control_plane.management.identity.verification.handler;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.identity.verification.IdentityVerificationConfigManagementContextBuilder;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigManagementResponse;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigManagementStatus;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigUpdateRequest;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfigurationIdentifier;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationConfigurationCommandRepository;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationConfigurationQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonDiffCalculator;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for identity verification configuration update operations.
 *
 * <p>Handles business logic for updating identity verification configurations. Part of
 * Handler/Service pattern.
 */
public class IdentityVerificationConfigUpdateService
    implements IdentityVerificationConfigManagementService<
        IdentityVerificationConfigUpdateRequest> {

  private final IdentityVerificationConfigurationQueryRepository queryRepository;
  private final IdentityVerificationConfigurationCommandRepository commandRepository;

  public IdentityVerificationConfigUpdateService(
      IdentityVerificationConfigurationQueryRepository queryRepository,
      IdentityVerificationConfigurationCommandRepository commandRepository) {
    this.queryRepository = queryRepository;
    this.commandRepository = commandRepository;
  }

  @Override
  public IdentityVerificationConfigManagementResponse execute(
      IdentityVerificationConfigManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      IdentityVerificationConfigUpdateRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // 1. Retrieve existing configuration (throws ResourceNotFoundException if not found)
    IdentityVerificationConfigurationIdentifier identifier = request.identifier();
    IdentityVerificationConfiguration before = queryRepository.find(tenant, identifier);

    if (!before.exists()) {
      throw new ResourceNotFoundException(
          "Identity verification configuration not found: " + identifier.value());
    }

    // 2. Create updated configuration
    IdentityVerificationConfiguration after = updateConfiguration(before, request.configRequest());

    // 3. Populate builder with before/after
    builder.withBefore(before);
    builder.withAfter(after);

    // 4. Build response
    JsonNodeWrapper beforeJson = JsonNodeWrapper.fromMap(before.toMap());
    JsonNodeWrapper afterJson = JsonNodeWrapper.fromMap(after.toMap());
    Map<String, Object> contents = new HashMap<>();
    contents.put("result", after.toMap());
    contents.put("diff", JsonDiffCalculator.deepDiff(beforeJson, afterJson));
    contents.put("dry_run", dryRun);

    if (dryRun) {
      return new IdentityVerificationConfigManagementResponse(
          IdentityVerificationConfigManagementStatus.OK, contents);
    }

    // 5. Repository operation
    commandRepository.update(tenant, after.type(), after);

    return new IdentityVerificationConfigManagementResponse(
        IdentityVerificationConfigManagementStatus.OK, contents);
  }

  private IdentityVerificationConfiguration updateConfiguration(
      IdentityVerificationConfiguration before,
      org.idp.server.control_plane.management.identity.verification.io
              .IdentityVerificationConfigRegistrationRequest
          request) {

    // Merge update request with existing configuration
    Map<String, Object> updatedMap = new HashMap<>(before.toMap());
    Map<String, Object> requestMap = request.toMap();

    // Update fields from request (preserving ID)
    requestMap.forEach(
        (key, value) -> {
          if (!"id".equals(key)) { // ID cannot be changed
            updatedMap.put(key, value);
          }
        });

    // Convert to IdentityVerificationConfiguration using JsonConverter
    JsonConverter converter = JsonConverter.snakeCaseInstance();
    return converter.read(updatedMap, IdentityVerificationConfiguration.class);
  }
}
