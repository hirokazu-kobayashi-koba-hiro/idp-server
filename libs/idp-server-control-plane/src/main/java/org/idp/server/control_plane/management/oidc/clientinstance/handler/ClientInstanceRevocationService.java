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

import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.idp.server.control_plane.management.exception.InvalidRequestException;
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
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Revokes a Client Instance: the instance stops authenticating, and its record stays.
 *
 * <p>Revocation and deletion answer different needs. Revocation stops trusting an instance — a lost
 * device, a compromised key — and keeps who registered it, when, and with what evidence. The key
 * stays registered, so it cannot be registered again. Deletion forgets the instance.
 *
 * <p>A revocation is final. Revoking an instance that is already revoked is refused rather than
 * silently accepted, so that the recorded revocation time stays the first one.
 */
public class ClientInstanceRevocationService
    implements ClientInstanceManagementService<ClientInstanceFindRequest> {

  private final ClientInstanceQueryRepository queryRepository;
  private final ClientInstanceCommandRepository commandRepository;

  public ClientInstanceRevocationService(
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
    if (clientInstance.isRevoked()) {
      throw new InvalidRequestException(
          "Client instance is already revoked: " + request.identifier().value());
    }

    // Truncated to the precision both databases store, so that the revocation time in this response
    // is the one later reads return.
    ClientInstance revoked =
        clientInstance.revoke(SystemDateTime.now().truncatedTo(ChronoUnit.MICROS));
    contextBuilder.withBefore(clientInstance).withAfter(revoked);

    if (dryRun) {
      return new ClientInstanceManagementResponse(
          ClientInstanceManagementStatus.OK,
          Map.of(
              "message",
              "Revocation simulated successfully",
              "result",
              revoked.toMap(),
              "dry_run",
              true));
    }

    commandRepository.update(tenant, revoked);

    return new ClientInstanceManagementResponse(ClientInstanceManagementStatus.OK, revoked.toMap());
  }
}
