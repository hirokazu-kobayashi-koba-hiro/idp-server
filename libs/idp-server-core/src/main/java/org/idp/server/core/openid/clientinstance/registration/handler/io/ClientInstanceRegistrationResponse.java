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

package org.idp.server.core.openid.clientinstance.registration.handler.io;

import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.clientinstance.ClientInstance;
import org.idp.server.core.openid.clientinstance.ClientInstanceIdentifier;
import org.idp.server.core.openid.clientinstance.registration.ClientInstanceRegistrationChallenge;
import org.idp.server.core.openid.clientinstance.registration.ClientInstanceRegistrationResult;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;

/**
 * Response of the Client Instance registration endpoints.
 *
 * @param contents what the caller sees
 * @param requestedClientId the client this outcome concerns, empty when a rejected ticket leaves it
 *     unresolvable
 * @param instanceIdentifier the instance this outcome concerns, kept as a value rather than read
 *     back out of {@code contents}
 * @param userId the user a registered instance is bound to; null for every other outcome. Kept out
 *     of {@code contents}: the caller already knows who logged in, the security event is what needs
 *     it
 * @param auditReason why a request was rejected. Never serialized — {@code contents} says the same
 *     thing for every rejection. This carries the reason back to the use case so the security event
 *     can record it.
 * @param supersededInstances the user's instances a registration revoked, for the security events
 *     that record them. Never serialized: the instances belong to other devices of the user
 */
public record ClientInstanceRegistrationResponse(
    int statusCode,
    Map<String, Object> contents,
    RequestedClientId requestedClientId,
    ClientInstanceIdentifier instanceIdentifier,
    String userId,
    String auditReason,
    List<ClientInstanceIdentifier> supersededInstances) {

  /** The ticket, plus the instance identifier it reserves. */
  public static ClientInstanceRegistrationResponse challengeIssued(
      ClientInstanceRegistrationChallenge challenge, int expiresInSeconds) {
    return new ClientInstanceRegistrationResponse(
        200,
        Map.of(
            "challenge", challenge.challenge(),
            "instance_id", challenge.instanceId(),
            "expires_in", expiresInSeconds),
        new RequestedClientId(challenge.clientId()),
        new ClientInstanceIdentifier(challenge.instanceId()),
        null,
        null,
        List.of());
  }

  /** The instance id was already returned with the challenge, so the body carries no secret. */
  public static ClientInstanceRegistrationResponse registered(
      ClientInstanceRegistrationResult result) {
    ClientInstance clientInstance = result.registered();
    return new ClientInstanceRegistrationResponse(
        201,
        Map.of("instance_id", clientInstance.id()),
        new RequestedClientId(clientInstance.clientId()),
        clientInstance.identifier(),
        clientInstance.userId(),
        null,
        result.superseded().stream().map(ClientInstance::identifier).toList());
  }

  /**
   * Registration failures are reported without distinguishing the cause.
   *
   * <p>The challenge endpoint is unauthenticated, so a detailed reason would let a caller probe
   * which clients exist or take part in registration, and which step of an ID token or evidence
   * check a forged request got past.
   */
  public static ClientInstanceRegistrationResponse invalidRequest(String auditReason) {
    return new ClientInstanceRegistrationResponse(
        400,
        Map.of("error", "invalid_request"),
        new RequestedClientId(),
        new ClientInstanceIdentifier(),
        null,
        auditReason,
        List.of());
  }

  /**
   * A fault on this side, kept apart from the caller's mistakes.
   *
   * <p>Returning invalid_request for everything would hide an outage behind a code that tells the
   * client to fix its request, and would leave a retry looking pointless when it is the only thing
   * that helps.
   */
  public static ClientInstanceRegistrationResponse serverError(String auditReason) {
    return new ClientInstanceRegistrationResponse(
        500,
        Map.of("error", "server_error"),
        new RequestedClientId(),
        new ClientInstanceIdentifier(),
        null,
        auditReason,
        List.of());
  }

  public boolean isSuccess() {
    return statusCode == 200 || statusCode == 201;
  }

  public boolean isError() {
    return !isSuccess();
  }
}
