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

import org.idp.server.control_plane.management.oidc.client.ClientRegistrationContext;
import org.idp.server.control_plane.management.oidc.client.ClientRegistrationContextCreator;
import org.idp.server.control_plane.management.oidc.client.io.ClientManagementResponse;
import org.idp.server.control_plane.management.oidc.client.io.ClientRegistrationRequest;
import org.idp.server.control_plane.management.oidc.client.validator.ClientRegistrationRequestValidator;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationCommandRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for client creation operations.
 *
 * <p>Handles business logic for creating client configurations. Part of Handler/Service pattern.
 */
public class ClientCreationService implements ClientManagementService<ClientRegistrationRequest> {

  private final ClientConfigurationCommandRepository commandRepository;

  public ClientCreationService(ClientConfigurationCommandRepository commandRepository) {
    this.commandRepository = commandRepository;
  }

  @Override
  public ClientManagementResult execute(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      ClientRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // Validation
    ClientRegistrationRequestValidator validator =
        new ClientRegistrationRequestValidator(request, dryRun);
    validator.validate(); // throws InvalidRequestException if invalid

    // Create context
    ClientRegistrationContextCreator contextCreator =
        new ClientRegistrationContextCreator(tenant, request, dryRun);
    ClientRegistrationContext context = contextCreator.create();

    if (dryRun) {
      ClientManagementResponse response = context.toResponse();
      return ClientManagementResult.success(tenant.identifier(), response, context);
    }

    // Register client
    commandRepository.register(tenant, context.configuration());

    ClientManagementResponse response = context.toResponse();
    return ClientManagementResult.success(tenant.identifier(), response, context);
  }
}
