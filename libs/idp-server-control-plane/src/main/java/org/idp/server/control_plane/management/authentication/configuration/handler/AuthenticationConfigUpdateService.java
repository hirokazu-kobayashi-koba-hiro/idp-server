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
import org.idp.server.control_plane.management.authentication.configuration.AuthenticationConfigManagementContextBuilder;
import org.idp.server.control_plane.management.authentication.configuration.io.AuthenticationConfigManagementResponse;
import org.idp.server.control_plane.management.authentication.configuration.io.AuthenticationConfigManagementStatus;
import org.idp.server.control_plane.management.authentication.configuration.io.AuthenticationConfigUpdateRequest;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.core.openid.authentication.config.AuthenticationConfiguration;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationCommandRepository;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonDiffCalculator;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for authentication policy configuration update operations.
 *
 * <p>Handles business logic for updating authentication policy configurations. Part of
 * Handler/Service pattern.
 */
public class AuthenticationConfigUpdateService
    implements AuthenticationConfigManagementService<AuthenticationConfigUpdateRequest> {

  private final AuthenticationConfigurationQueryRepository queryRepository;
  private final AuthenticationConfigurationCommandRepository commandRepository;
  private final JsonConverter jsonConverter;

  public AuthenticationConfigUpdateService(
      AuthenticationConfigurationQueryRepository queryRepository,
      AuthenticationConfigurationCommandRepository commandRepository) {
    this.queryRepository = queryRepository;
    this.commandRepository = commandRepository;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public AuthenticationConfigManagementResponse execute(
      AuthenticationConfigManagementContextBuilder contextBuilder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationConfigUpdateRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // Find existing configuration
    AuthenticationConfiguration before =
        queryRepository.findWithDisabled(tenant, request.identifier(), true);

    if (!before.exists()) {
      throw new ResourceNotFoundException(
          "Authentication policy configuration not found: " + request.identifier().value());
    }

    // Build updated configuration
    AuthenticationConfiguration after = update(before, request);

    // Update context builder with before and after states
    contextBuilder.withBefore(before).withAfter(after);

    JsonNodeWrapper beforeJson = JsonNodeWrapper.fromMap(before.toMap());
    JsonNodeWrapper afterJson = JsonNodeWrapper.fromMap(after.toMap());
    Map<String, Object> diff = JsonDiffCalculator.deepDiff(beforeJson, afterJson);
    Map<String, Object> response = Map.of("result", after.toMap(), "diff", diff, "dry_run", dryRun);

    if (dryRun) {
      return new AuthenticationConfigManagementResponse(
          AuthenticationConfigManagementStatus.OK, response);
    }

    // Update configuration
    commandRepository.update(tenant, after);

    return new AuthenticationConfigManagementResponse(
        AuthenticationConfigManagementStatus.OK, response);
  }

  public AuthenticationConfiguration update(
      AuthenticationConfiguration before, AuthenticationConfigUpdateRequest request) {

    String id = before.id();

    Map<String, Object> configMap = new HashMap<>(request.toMap());
    configMap.put("id", id);

    return jsonConverter.read(configMap, AuthenticationConfiguration.class);
  }
}
