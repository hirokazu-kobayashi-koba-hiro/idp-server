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

import java.util.Map;
import java.util.UUID;
import org.idp.server.control_plane.management.identity.verification.IdentityVerificationConfigManagementContextBuilder;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigManagementResponse;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigManagementStatus;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigRegistrationRequest;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigurationRequest;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationConfigurationCommandRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for identity verification configuration creation operations.
 *
 * <p>Handles business logic for creating identity verification configurations. Part of
 * Handler/Service pattern.
 */
public class IdentityVerificationConfigCreationService
    implements IdentityVerificationConfigManagementService<
        IdentityVerificationConfigRegistrationRequest> {

  private final IdentityVerificationConfigurationCommandRepository commandRepository;

  public IdentityVerificationConfigCreationService(
      IdentityVerificationConfigurationCommandRepository commandRepository) {
    this.commandRepository = commandRepository;
  }

  @Override
  public IdentityVerificationConfigManagementResponse execute(
      IdentityVerificationConfigManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      IdentityVerificationConfigRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // 1. Create configuration from request
    IdentityVerificationConfiguration configuration = createConfiguration(request);

    // 2. Populate builder with created configuration
    builder.withAfter(configuration);

    // 3. Build response
    Map<String, Object> contents = Map.of("result", configuration.toMap(), "dry_run", dryRun);

    if (dryRun) {
      return new IdentityVerificationConfigManagementResponse(
          IdentityVerificationConfigManagementStatus.OK, contents);
    }

    // 4. Repository operation
    commandRepository.register(tenant, configuration.type(), configuration);

    return new IdentityVerificationConfigManagementResponse(
        IdentityVerificationConfigManagementStatus.CREATED, contents);
  }

  private IdentityVerificationConfiguration createConfiguration(
      IdentityVerificationConfigRegistrationRequest request) {
    JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
    IdentityVerificationConfigurationRequest configurationRequest =
        jsonConverter.read(request.toMap(), IdentityVerificationConfigurationRequest.class);

    String identifier =
        configurationRequest.hasId() ? configurationRequest.id() : UUID.randomUUID().toString();

    return configurationRequest.toConfiguration(identifier);
  }
}
