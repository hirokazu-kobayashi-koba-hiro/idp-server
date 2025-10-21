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

import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.oidc.client.ClientUpdateContext;
import org.idp.server.control_plane.management.oidc.client.ClientUpdateContextCreator;
import org.idp.server.control_plane.management.oidc.client.io.ClientManagementResponse;
import org.idp.server.control_plane.management.oidc.client.validator.ClientRegistrationRequestValidator;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationCommandRepository;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.openid.token.OAuthToken;
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

  public ClientUpdateService(
      ClientConfigurationQueryRepository queryRepository,
      ClientConfigurationCommandRepository commandRepository) {
    this.queryRepository = queryRepository;
    this.commandRepository = commandRepository;
  }

  @Override
  public ClientManagementResult execute(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      ClientUpdateRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // Find existing client
    ClientConfiguration before =
        queryRepository.findWithDisabled(tenant, request.clientIdentifier(), true);

    if (!before.exists()) {
      throw new ResourceNotFoundException(
          "Client not found: " + request.clientIdentifier().value());
    }

    // Validation
    ClientRegistrationRequestValidator validator =
        new ClientRegistrationRequestValidator(request.registrationRequest(), dryRun);
    validator.validate(); // throws InvalidRequestException if invalid

    // Create context
    ClientUpdateContextCreator contextCreator =
        new ClientUpdateContextCreator(tenant, before, request.registrationRequest(), dryRun);
    ClientUpdateContext context = contextCreator.create();

    if (dryRun) {
      ClientManagementResponse response = context.toResponse();
      return ClientManagementResult.success(tenant.identifier(), response, context);
    }

    // Update client
    commandRepository.update(tenant, context.after());

    ClientManagementResponse response = context.toResponse();
    return ClientManagementResult.success(tenant.identifier(), response, context);
  }
}
