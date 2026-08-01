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

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.idp.server.control_plane.management.exception.InvalidRequestException;
import org.idp.server.control_plane.management.oidc.clientinstance.ClientInstanceManagementContextBuilder;
import org.idp.server.control_plane.management.oidc.clientinstance.io.ClientInstanceManagementResponse;
import org.idp.server.control_plane.management.oidc.clientinstance.io.ClientInstanceManagementStatus;
import org.idp.server.control_plane.management.oidc.clientinstance.io.ClientInstanceRegistrationRequest;
import org.idp.server.core.openid.clientinstance.ClientInstance;
import org.idp.server.core.openid.clientinstance.ClientInstanceCommandRepository;
import org.idp.server.core.openid.clientinstance.ClientInstanceStatus;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.date.LocalDateTimeParser;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Registers a Client Instance Key.
 *
 * <p>Only public key material is accepted: the registered key becomes a trust anchor for
 * self-signed Client Attestation JWTs, and a private component must never reach the server.
 */
public class ClientInstanceRegistrationService
    implements ClientInstanceManagementService<ClientInstanceRegistrationRequest> {

  private final ClientInstanceCommandRepository commandRepository;

  public ClientInstanceRegistrationService(ClientInstanceCommandRepository commandRepository) {
    this.commandRepository = commandRepository;
  }

  @Override
  public ClientInstanceManagementResponse execute(
      ClientInstanceManagementContextBuilder contextBuilder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      ClientInstanceRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    Map<String, Object> instanceKey = request.instanceKey();
    throwExceptionIfInvalidInstanceKey(instanceKey);

    String id = request.id() != null ? request.id() : UUID.randomUUID().toString();
    LocalDateTime expiresAt =
        request.expiresAt() != null ? LocalDateTimeParser.parse(request.expiresAt()) : null;

    ClientInstance clientInstance =
        new ClientInstance(
            id,
            tenant.identifierValue(),
            request.requestedClientId().value(),
            instanceKey,
            ClientInstanceStatus.active.name(),
            request.attestationEvidence(),
            request.deviceId(),
            null,
            null,
            expiresAt,
            null);

    contextBuilder.withAfter(clientInstance);

    if (dryRun) {
      return new ClientInstanceManagementResponse(
          ClientInstanceManagementStatus.OK,
          Map.of("result", clientInstance.toMap(), "dry_run", true));
    }

    commandRepository.register(tenant, clientInstance);

    return new ClientInstanceManagementResponse(
        ClientInstanceManagementStatus.CREATED,
        Map.of("result", clientInstance.toMap(), "dry_run", false));
  }

  private void throwExceptionIfInvalidInstanceKey(Map<String, Object> instanceKey) {
    if (instanceKey.isEmpty()) {
      throw new InvalidRequestException("instance_key is required");
    }
    if (!instanceKey.containsKey("kty")) {
      throw new InvalidRequestException("instance_key must be a JWK containing kty");
    }
    // JWK private key components (RFC 7517/7518): d for EC/OKP/RSA, and the RSA CRT values
    for (String privateMember : new String[] {"d", "p", "q", "dp", "dq", "qi", "k"}) {
      if (instanceKey.containsKey(privateMember)) {
        throw new InvalidRequestException(
            "instance_key must not contain private key material: " + privateMember);
      }
    }
  }
}
