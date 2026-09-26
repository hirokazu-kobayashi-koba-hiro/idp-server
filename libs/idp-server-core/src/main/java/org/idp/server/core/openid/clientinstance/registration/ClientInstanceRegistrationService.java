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

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.clientinstance.ClientInstance;
import org.idp.server.core.openid.clientinstance.ClientInstanceCommandRepository;
import org.idp.server.core.openid.clientinstance.ClientInstanceQueryRepository;
import org.idp.server.core.openid.clientinstance.ClientInstanceRevocationReason;
import org.idp.server.core.openid.clientinstance.ClientInstanceStatus;
import org.idp.server.core.openid.clientinstance.ClientInstanceThumbprint;
import org.idp.server.core.openid.clientinstance.registration.verifier.ClientInstanceRegistrationIdTokenVerifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.jose.JsonWebTokenClaims;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Registers a Client Instance from an end-user application and binds it to the user.
 *
 * <p>What authorizes the registration is the combination of a server issued ticket, an ID token and
 * the platform attestation, all three bound to one another:
 *
 * <ol>
 *   <li>The challenge is consumed atomically, so a captured request cannot be replayed
 *   <li>client_id / instance id come from the ticket, never from the request body
 *   <li>The ID token identifies the user, and its {@code nonce} is the request hash of the ticket
 *       and the key, so it authenticates the registration of this key only ({@link
 *       ClientInstanceRegistrationIdTokenVerifier})
 *   <li>The platform attestation must bind the challenge, the instance key and the application
 *       identity ({@link PlatformAttestationVerifier})
 * </ol>
 *
 * <p>A user holds one active instance of a client. The new instance takes the place of the user's
 * others, which are revoked in the same transaction. There is no way to tell whether it is the same
 * device: an instance is a registration of a key, not a device. Two registrations running at once
 * cannot both leave an active instance; the database constrains that, and the later one fails.
 *
 * <p>The tokens of a replaced instance are left alone. Revoking it already stops the device the
 * user moved away from at its next client authentication, a refresh included; deleting its tokens
 * would sign it out as well, which is an explicit act — the management API's revocation and
 * deletion do it — rather than a side effect of registering another device.
 */
public class ClientInstanceRegistrationService {

  LoggerWrapper log = LoggerWrapper.getLogger(ClientInstanceRegistrationService.class);

  ClientInstanceRegistrationChallengeRepository challengeRepository;
  ClientInstanceQueryRepository clientInstanceQueryRepository;
  ClientInstanceCommandRepository clientInstanceCommandRepository;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;
  AuthorizationServerConfigurationQueryRepository authorizationServerConfigurationQueryRepository;
  UserQueryRepository userQueryRepository;
  ClientInstanceRegistrationIdTokenVerifier idTokenVerifier;
  PlatformAttestationVerifiers verifiers;

  public ClientInstanceRegistrationService(
      ClientInstanceRegistrationChallengeRepository challengeRepository,
      ClientInstanceQueryRepository clientInstanceQueryRepository,
      ClientInstanceCommandRepository clientInstanceCommandRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository,
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository,
      UserQueryRepository userQueryRepository,
      ClientInstanceRegistrationIdTokenVerifier idTokenVerifier,
      PlatformAttestationVerifiers verifiers) {
    this.challengeRepository = challengeRepository;
    this.clientInstanceQueryRepository = clientInstanceQueryRepository;
    this.clientInstanceCommandRepository = clientInstanceCommandRepository;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
    this.authorizationServerConfigurationQueryRepository =
        authorizationServerConfigurationQueryRepository;
    this.userQueryRepository = userQueryRepository;
    this.idTokenVerifier = idTokenVerifier;
    this.verifiers = verifiers;
  }

  public ClientInstanceRegistrationResult register(
      Tenant tenant,
      String challengeValue,
      Map<String, Object> instanceKey,
      Map<String, Object> platformEvidence,
      String idToken) {

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
    throwExceptionIfKeyIsAlreadyRegistered(tenant, instanceKey);

    ClientConfiguration clientConfiguration =
        clientConfigurationQueryRepository.get(tenant, challenge.requestedClientId());
    AuthorizationServerConfiguration serverConfiguration =
        authorizationServerConfigurationQueryRepository.get(tenant);

    JsonWebTokenClaims idTokenClaims =
        idTokenVerifier.verify(
            serverConfiguration, clientConfiguration, challenge, instanceKey, idToken);
    User user = resolveUser(tenant, idTokenClaims);

    PlatformAttestationVerifier verifier = verifiers.get(platform(platformEvidence));
    PlatformAttestationEvidence evidence =
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
            evidence.toMap(SystemDateTime.now()),
            null,
            user.sub(),
            null,
            null,
            expiresAtOf(clientConfiguration),
            null);

    // Before the insert: the database admits one active instance per user and client.
    List<ClientInstance> superseded = supersedeActiveInstancesOf(tenant, clientInstance);
    clientInstanceCommandRepository.register(tenant, clientInstance);

    log.info(
        "Client instance registered: client_id={}, instance_id={}, user={}",
        challenge.clientId(),
        challenge.instanceId(),
        user.sub());

    return new ClientInstanceRegistrationResult(clientInstance, superseded);
  }

  /**
   * When the instance stops being usable, from the client's {@code
   * client_instance_lifetime_seconds}; {@code null} (no expiry) when it sets none.
   */
  private LocalDateTime expiresAtOf(ClientConfiguration clientConfiguration) {
    if (!clientConfiguration.hasClientInstanceLifetime()) {
      return null;
    }
    return SystemDateTime.now()
        .plusSeconds(clientConfiguration.clientInstanceLifetimeSeconds())
        .truncatedTo(ChronoUnit.MICROS);
  }

  /**
   * Revokes the user's other active instances of the client. Their next client authentication
   * fails, so their refresh tokens stop with them.
   */
  private List<ClientInstance> supersedeActiveInstancesOf(
      Tenant tenant, ClientInstance newInstance) {
    List<ClientInstance> actives =
        clientInstanceQueryRepository.findActiveListByUser(
            tenant, newInstance.requestedClientId(), newInstance.userId());

    LocalDateTime revokedAt = SystemDateTime.now().truncatedTo(ChronoUnit.MICROS);
    for (ClientInstance active : actives) {
      clientInstanceCommandRepository.update(
          tenant, active.revoke(revokedAt, ClientInstanceRevocationReason.superseded));
    }

    if (!actives.isEmpty()) {
      log.info(
          "Client instances superseded: client_id={}, user={}, count={}",
          newInstance.clientId(),
          newInstance.userId(),
          actives.size());
    }
    return actives;
  }

  /**
   * Resolves the user the ID token names. The user is looked up by the internal identifier rather
   * than trusted from the token alone: a user deleted or suspended since the login must not end up
   * owning a new instance.
   */
  private User resolveUser(Tenant tenant, JsonWebTokenClaims idTokenClaims) {
    User user = userQueryRepository.findById(tenant, new UserIdentifier(idTokenClaims.getSub()));
    if (!user.isActive()) {
      throw new ClientInstanceRegistrationException(
          "the user of the id_token does not exist or is not active");
    }
    return user;
  }

  private String platform(Map<String, Object> platformEvidence) {
    Object platform = platformEvidence.get("platform");
    if (!(platform instanceof String value) || value.isEmpty()) {
      throw new ClientInstanceRegistrationException("platform_evidence.platform is required");
    }
    return value;
  }

  /**
   * A key belongs to at most one instance within a tenant, revoked ones included. A refresh token
   * is bound to the key, so a second instance holding it would redeem the tokens of the first, and
   * revoking the first would not stop the key.
   */
  private void throwExceptionIfKeyIsAlreadyRegistered(
      Tenant tenant, Map<String, Object> instanceKey) {
    ClientInstanceThumbprint thumbprint = ClientInstanceThumbprint.of(instanceKey);
    if (!thumbprint.exists()) {
      throw new ClientInstanceRegistrationException(
          "client_instance_public_key is not a valid JWK");
    }
    if (clientInstanceQueryRepository.findByThumbprint(tenant, thumbprint).exists()) {
      throw new ClientInstanceRegistrationException(
          "client_instance_public_key is already registered to an instance of this tenant");
    }
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
}
