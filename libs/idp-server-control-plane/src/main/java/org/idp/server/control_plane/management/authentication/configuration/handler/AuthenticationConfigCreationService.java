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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.idp.server.control_plane.management.authentication.configuration.AuthenticationConfigManagementContextBuilder;
import org.idp.server.control_plane.management.authentication.configuration.io.AuthenticationConfigManagementResponse;
import org.idp.server.control_plane.management.authentication.configuration.io.AuthenticationConfigManagementStatus;
import org.idp.server.control_plane.management.authentication.configuration.io.AuthenticationConfigRequest;
import org.idp.server.core.openid.authentication.config.AuthenticationConfiguration;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationCommandRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for creating authentication policy configurations.
 *
 * <p>Handles business logic for creating authentication policy configurations. Part of
 * Handler/Service pattern.
 */
public class AuthenticationConfigCreationService
    implements AuthenticationConfigManagementService<AuthenticationConfigRequest> {

  private final AuthenticationConfigurationCommandRepository commandRepository;
  private final JsonConverter jsonConverter;

  public AuthenticationConfigCreationService(
      AuthenticationConfigurationCommandRepository commandRepository) {
    this.commandRepository = commandRepository;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public AuthenticationConfigManagementResponse execute(
      AuthenticationConfigManagementContextBuilder contextBuilder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationConfigRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // Build AuthenticationConfiguration (add id if missing)
    Map<String, Object> map = new HashMap<>(request.toMap());
    if (!request.hasId()) {
      map.put("id", UUID.randomUUID().toString());
    }
    AuthenticationConfiguration configuration =
        jsonConverter.read(map, AuthenticationConfiguration.class);

    // Update context builder with after state
    contextBuilder.withAfter(configuration);

    Map<String, Object> response = Map.of("result", configuration.toMap(), "dry_run", dryRun);
    if (dryRun) {
      return new AuthenticationConfigManagementResponse(
          AuthenticationConfigManagementStatus.OK, response);
    }

    // Register configuration
    commandRepository.register(tenant, configuration);

    return new AuthenticationConfigManagementResponse(
        AuthenticationConfigManagementStatus.CREATED, response);
  }
}
