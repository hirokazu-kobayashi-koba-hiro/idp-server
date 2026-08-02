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

package org.idp.server.control_plane.management.oidc.clientinstance.handler;

import java.util.Map;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.oidc.clientinstance.ClientInstanceManagementContextBuilder;
import org.idp.server.control_plane.management.oidc.clientinstance.io.ClientInstanceFindRequest;
import org.idp.server.control_plane.management.oidc.clientinstance.io.ClientInstanceManagementResponse;
import org.idp.server.control_plane.management.oidc.clientinstance.io.ClientInstanceManagementStatus;
import org.idp.server.core.openid.clientinstance.ClientInstance;
import org.idp.server.core.openid.clientinstance.ClientInstanceCommandRepository;
import org.idp.server.core.openid.clientinstance.ClientInstanceQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Deletes a registered Client Instance.
 *
 * <p>The key is resolved on every authentication, so removal takes effect from the next request.
 */
public class ClientInstanceDeletionService
    implements ClientInstanceManagementService<ClientInstanceFindRequest> {

  private final ClientInstanceQueryRepository queryRepository;
  private final ClientInstanceCommandRepository commandRepository;

  public ClientInstanceDeletionService(
      ClientInstanceQueryRepository queryRepository,
      ClientInstanceCommandRepository commandRepository) {
    this.queryRepository = queryRepository;
    this.commandRepository = commandRepository;
  }

  @Override
  public ClientInstanceManagementResponse execute(
      ClientInstanceManagementContextBuilder contextBuilder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      ClientInstanceFindRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    ClientInstance clientInstance =
        queryRepository.find(tenant, request.requestedClientId(), request.identifier());

    if (!clientInstance.exists()) {
      throw new ResourceNotFoundException(
          "Client instance not found: " + request.identifier().value());
    }

    contextBuilder.withBefore(clientInstance);

    if (dryRun) {
      return new ClientInstanceManagementResponse(
          ClientInstanceManagementStatus.OK,
          Map.of(
              "message",
              "Deletion simulated successfully",
              "id",
              clientInstance.id(),
              "dry_run",
              true));
    }

    commandRepository.delete(tenant, request.requestedClientId(), request.identifier());

    return new ClientInstanceManagementResponse(
        ClientInstanceManagementStatus.NO_CONTENT, Map.of());
  }
}
