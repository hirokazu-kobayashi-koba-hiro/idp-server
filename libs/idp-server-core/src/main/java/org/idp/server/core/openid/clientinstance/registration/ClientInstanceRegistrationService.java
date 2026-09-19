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

package org.idp.server.core.openid.clientinstance.registration;

import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.clientinstance.ClientInstance;
import org.idp.server.core.openid.clientinstance.ClientInstanceCommandRepository;
import org.idp.server.core.openid.clientinstance.ClientInstanceQueryRepository;
import org.idp.server.core.openid.clientinstance.ClientInstanceStatus;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Registers a Client Instance from an end-user application.
 *
 * <p>The endpoint is unauthenticated. What authorizes the registration is the combination of a
 * server issued ticket and the platform attestation bound to it:
 *
 * <ol>
 *   <li>The challenge is consumed atomically, so a captured request cannot be replayed
 *   <li>client_id / device_id / instance id come from the ticket, never from the request body
 *   <li>The device must not already hold an active instance, so a stolen piece of evidence cannot
 *       silently add a second key alongside the legitimate one
 *   <li>The platform attestation must bind the challenge, the instance key and the application
 *       identity ({@link PlatformAttestationVerifier})
 * </ol>
 */
public class ClientInstanceRegistrationService {

  LoggerWrapper log = LoggerWrapper.getLogger(ClientInstanceRegistrationService.class);

  ClientInstanceRegistrationChallengeRepository challengeRepository;
  ClientInstanceQueryRepository clientInstanceQueryRepository;
  ClientInstanceCommandRepository clientInstanceCommandRepository;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;
  PlatformAttestationVerifiers verifiers;

  public ClientInstanceRegistrationService(
      ClientInstanceRegistrationChallengeRepository challengeRepository,
      ClientInstanceQueryRepository clientInstanceQueryRepository,
      ClientInstanceCommandRepository clientInstanceCommandRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository,
      PlatformAttestationVerifiers verifiers) {
    this.challengeRepository = challengeRepository;
    this.clientInstanceQueryRepository = clientInstanceQueryRepository;
    this.clientInstanceCommandRepository = clientInstanceCommandRepository;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
    this.verifiers = verifiers;
  }

  public ClientInstance register(
      Tenant tenant,
      String challengeValue,
      Map<String, Object> instanceKey,
      Map<String, Object> platformEvidence) {

    ClientInstanceRegistrationChallenge challenge =
        challengeRepository.find(tenant, challengeValue);

    if (!challenge.isConsumable()) {
      throw new ClientInstanceRegistrationException(
          "registration challenge is unknown, expired or already used");
    }

    // Consume before verifying: a failed verification must not leave the ticket reusable.
    if (!challengeRepository.consume(tenant, challengeValue)) {
      throw new ClientInstanceRegistrationException("registration challenge has already been used");
    }

    throwExceptionIfInvalidInstanceKey(instanceKey);
    throwExceptionIfDeviceAlreadyHasActiveInstance(tenant, challenge);

    ClientConfiguration clientConfiguration =
        clientConfigurationQueryRepository.get(tenant, challenge.requestedClientId());

    PlatformAttestationVerifier verifier = verifiers.get(platform(platformEvidence));
    verifier.verify(
        new PlatformAttestationVerificationRequest(
            tenant, clientConfiguration, challenge, instanceKey, platformEvidence));

    ClientInstance clientInstance =
        new ClientInstance(
            challenge.instanceId(),
            tenant.identifierValue(),
            challenge.clientId(),
            instanceKey,
            ClientInstanceStatus.active.name(),
            Map.of(),
            challenge.deviceId(),
            null,
            null,
            null,
            null);

    clientInstanceCommandRepository.register(tenant, clientInstance);

    log.info(
        "Client instance registered: client_id={}, instance_id={}",
        challenge.clientId(),
        challenge.instanceId());

    return clientInstance;
  }

  private String platform(Map<String, Object> platformEvidence) {
    Object platform = platformEvidence.get("platform");
    if (!(platform instanceof String value) || value.isEmpty()) {
      throw new ClientInstanceRegistrationException("platform_evidence.platform is required");
    }
    return value;
  }

  private void throwExceptionIfInvalidInstanceKey(Map<String, Object> instanceKey) {
    if (instanceKey == null || instanceKey.isEmpty() || !instanceKey.containsKey("kty")) {
      throw new ClientInstanceRegistrationException(
          "client_instance_public_key must be a JWK containing kty");
    }
    // JWK private members (RFC 7517 / 7518): the registered key becomes a trust anchor, so private
    // material must never be accepted.
    for (String privateMember : List.of("d", "p", "q", "dp", "dq", "qi", "k")) {
      if (instanceKey.containsKey(privateMember)) {
        throw new ClientInstanceRegistrationException(
            "client_instance_public_key must not contain private key material: " + privateMember);
      }
    }
  }

  private void throwExceptionIfDeviceAlreadyHasActiveInstance(
      Tenant tenant, ClientInstanceRegistrationChallenge challenge) {
    if (!challenge.hasDeviceId()) {
      return;
    }

    List<ClientInstance> activeInstances =
        clientInstanceQueryRepository.findActiveListByDevice(
            tenant, challenge.requestedClientId(), challenge.deviceId());

    if (!activeInstances.isEmpty()) {
      throw new ClientInstanceRegistrationException(
          "device already holds an active client instance; revoke it before re-registering");
    }
  }
}
