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

package org.idp.server.control_plane.management.oidc.authorization.handler;

import java.util.Map;
import org.idp.server.control_plane.management.oidc.authorization.AuthorizationServerManagementContextBuilder;
import org.idp.server.control_plane.management.oidc.authorization.io.AuthorizationServerManagementResponse;
import org.idp.server.control_plane.management.oidc.authorization.io.AuthorizationServerManagementStatus;
import org.idp.server.control_plane.management.oidc.authorization.io.AuthorizationServerUpdateRequest;
import org.idp.server.control_plane.management.oidc.authorization.validator.AuthorizationServerRequestValidator;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationCommandRepository;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonDiffCalculator;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for authorization server configuration update operations.
 *
 * <p>Handles business logic for updating authorization server configurations. Part of
 * Handler/Service pattern.
 */
public class AuthorizationServerUpdateService
    implements AuthorizationServerManagementService<AuthorizationServerUpdateRequest> {

  private final AuthorizationServerConfigurationQueryRepository queryRepository;
  private final AuthorizationServerConfigurationCommandRepository commandRepository;
  private final JsonConverter jsonConverter;

  public AuthorizationServerUpdateService(
      AuthorizationServerConfigurationQueryRepository queryRepository,
      AuthorizationServerConfigurationCommandRepository commandRepository) {
    this.queryRepository = queryRepository;
    this.commandRepository = commandRepository;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public AuthorizationServerManagementResponse execute(
      AuthorizationServerManagementContextBuilder contextBuilder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      AuthorizationServerUpdateRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // Validation
    AuthorizationServerRequestValidator validator =
        new AuthorizationServerRequestValidator(request, dryRun);
    validator.validate();

    // Get existing configuration
    AuthorizationServerConfiguration before = queryRepository.getWithDisabled(tenant, true);

    // Build updated configuration
    AuthorizationServerConfiguration after =
        jsonConverter.read(request.toMap(), AuthorizationServerConfiguration.class);

    // Update context builder with before and after states
    contextBuilder.withBefore(before).withAfter(after);

    JsonNodeWrapper beforeJson = JsonNodeWrapper.fromMap(before.toMap());
    JsonNodeWrapper afterJson = JsonNodeWrapper.fromMap(after.toMap());
    Map<String, Object> diff = JsonDiffCalculator.deepDiff(beforeJson, afterJson);
    Map<String, Object> response = Map.of("result", after.toMap(), "diff", diff, "dry_run", dryRun);

    if (dryRun) {
      return new AuthorizationServerManagementResponse(
          AuthorizationServerManagementStatus.OK, response);
    }

    // Update configuration
    commandRepository.update(tenant, after);

    return new AuthorizationServerManagementResponse(
        AuthorizationServerManagementStatus.OK, response);
  }
}
