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

package org.idp.server.usecases.application.enduser;

import java.util.Map;
import org.idp.server.core.openid.clientinstance.ClientInstance;
import org.idp.server.core.openid.clientinstance.registration.*;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Client Instance registration for end-user applications.
 *
 * <p>Both endpoints are unauthenticated: the challenge is an authorization ticket decided by the
 * server, and the platform attestation bound to it is what authenticates the registration.
 */
@Transaction
public class ClientInstanceRegistrationEntryService implements ClientInstanceRegistrationApi {

  static final int CHALLENGE_EXPIRES_IN_SECONDS = 300;

  LoggerWrapper log = LoggerWrapper.getLogger(ClientInstanceRegistrationEntryService.class);

  TenantQueryRepository tenantQueryRepository;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;
  ClientInstanceRegistrationChallengeRepository challengeRepository;
  ClientInstanceRegistrationChallengeIssuer challengeIssuer;
  ClientInstanceRegistrationService registrationService;

  public ClientInstanceRegistrationEntryService(
      TenantQueryRepository tenantQueryRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository,
      ClientInstanceRegistrationChallengeRepository challengeRepository,
      ClientInstanceRegistrationService registrationService) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
    this.challengeRepository = challengeRepository;
    this.challengeIssuer = new ClientInstanceRegistrationChallengeIssuer();
    this.registrationService = registrationService;
  }

  @Override
  public ClientInstanceRegistrationResponse challenge(
      TenantIdentifier tenantIdentifier,
      ClientInstanceChallengeRequest request,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    try {
      RequestedClientId requestedClientId = request.requestedClientId();
      String deviceId = request.deviceId();

      throwExceptionIfClientDoesNotUseAttestation(tenant, requestedClientId);

      ClientInstanceRegistrationChallenge challenge =
          challengeIssuer.issue(tenant, requestedClientId, deviceId, CHALLENGE_EXPIRES_IN_SECONDS);
      challengeRepository.register(tenant, challenge);

      return ClientInstanceRegistrationResponse.ok(
          Map.of(
              "challenge", challenge.challenge(),
              "instance_id", challenge.instanceId(),
              "expires_in", CHALLENGE_EXPIRES_IN_SECONDS));
    } catch (RuntimeException e) {
      log.warn("Client instance registration challenge rejected: {}", e.getMessage());
      return ClientInstanceRegistrationResponse.invalidRequest();
    }
  }

  @Override
  public ClientInstanceRegistrationResponse register(
      TenantIdentifier tenantIdentifier,
      ClientInstanceRegisterRequest request,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    try {
      ClientInstance clientInstance =
          registrationService.register(
              tenant, request.challenge(), request.instanceKey(), request.platformEvidence());

      // The instance id was already returned with the challenge, so the body carries no secret.
      return ClientInstanceRegistrationResponse.created(Map.of("instance_id", clientInstance.id()));
    } catch (RuntimeException e) {
      log.warn("Client instance registration rejected: {}", e.getMessage());
      return ClientInstanceRegistrationResponse.invalidRequest();
    }
  }

  private void throwExceptionIfClientDoesNotUseAttestation(
      Tenant tenant, RequestedClientId requestedClientId) {
    ClientConfiguration clientConfiguration =
        clientConfigurationQueryRepository.get(tenant, requestedClientId);

    if (!clientConfiguration.clientAuthenticationType().isAttestJwtClientAuth()) {
      throw new ClientInstanceRegistrationException(
          "client does not use attest_jwt_client_auth: " + requestedClientId.value());
    }
  }
}
