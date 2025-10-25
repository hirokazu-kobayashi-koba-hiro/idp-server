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

package org.idp.server.control_plane.management.oidc.client.handler;

import java.util.Map;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.oidc.client.ClientManagementContextBuilder;
import org.idp.server.control_plane.management.oidc.client.io.ClientManagementResponse;
import org.idp.server.control_plane.management.oidc.client.io.ClientManagementStatus;
import org.idp.server.control_plane.management.oidc.client.io.ClientUpdateRequest;
import org.idp.server.control_plane.management.oidc.client.validator.ClientRegistrationRequestValidator;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationCommandRepository;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonDiffCalculator;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for client update operations.
 *
 * <p>Handles business logic for updating client configurations. Part of Handler/Service pattern.
 */
public class ClientUpdateService implements ClientManagementService<ClientUpdateRequest> {

  private final ClientConfigurationQueryRepository queryRepository;
  private final ClientConfigurationCommandRepository commandRepository;
  private final JsonConverter jsonConverter;

  public ClientUpdateService(
      ClientConfigurationQueryRepository queryRepository,
      ClientConfigurationCommandRepository commandRepository) {
    this.queryRepository = queryRepository;
    this.commandRepository = commandRepository;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public ClientManagementResponse execute(
      ClientManagementContextBuilder contextBuilder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      ClientUpdateRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // Find existing client
    ClientConfiguration before =
        queryRepository.findWithDisabled(tenant, request.identifier(), true);

    if (!before.exists()) {
      throw new ResourceNotFoundException("Client not found: " + request.identifier().value());
    }

    // Validation
    ClientRegistrationRequestValidator validator =
        new ClientRegistrationRequestValidator(request.registrationRequest(), dryRun);
    validator.validate(); // throws InvalidRequestException if invalid

    // Build updated ClientConfiguration
    ClientConfiguration after =
        jsonConverter.read(request.registrationRequest().toMap(), ClientConfiguration.class);

    // Update context builder with before and after states
    contextBuilder.withBefore(before).withAfter(after);

    JsonNodeWrapper beforeJson = JsonNodeWrapper.fromMap(before.toMap());
    JsonNodeWrapper afterJson = JsonNodeWrapper.fromMap(after.toMap());
    Map<String, Object> diff = JsonDiffCalculator.deepDiff(beforeJson, afterJson);
    Map<String, Object> response = Map.of("result", after.toMap(), "diff", diff, "dry_run", dryRun);

    if (dryRun) {
      return new ClientManagementResponse(ClientManagementStatus.OK, response);
    }

    // Update client
    commandRepository.update(tenant, after);

    return new ClientManagementResponse(ClientManagementStatus.OK, response);
  }
}
