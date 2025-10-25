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
import org.idp.server.control_plane.management.oidc.client.ClientManagementContextBuilder;
import org.idp.server.control_plane.management.oidc.client.io.ClientFindRequest;
import org.idp.server.control_plane.management.oidc.client.io.ClientManagementResponse;
import org.idp.server.control_plane.management.oidc.client.io.ClientManagementStatus;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for client retrieval operations.
 *
 * <p>Handles business logic for retrieving a single client configuration. Part of Handler/Service
 * pattern.
 */
public class ClientFindService implements ClientManagementService<ClientFindRequest> {

  private final ClientConfigurationQueryRepository queryRepository;

  public ClientFindService(ClientConfigurationQueryRepository queryRepository) {
    this.queryRepository = queryRepository;
  }

  @Override
  public ClientManagementResponse execute(
      ClientManagementContextBuilder contextBuilder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      ClientFindRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    ClientConfiguration clientConfiguration =
        queryRepository.findWithDisabled(tenant, request.identifier(), true);

    if (!clientConfiguration.exists()) {
      throw new ResourceNotFoundException("Client not found: " + request.identifier().value());
    }

    // Update context builder with before state (for audit logging)
    contextBuilder.withBefore(clientConfiguration);

    return new ClientManagementResponse(ClientManagementStatus.OK, clientConfiguration.toMap());
  }
}
