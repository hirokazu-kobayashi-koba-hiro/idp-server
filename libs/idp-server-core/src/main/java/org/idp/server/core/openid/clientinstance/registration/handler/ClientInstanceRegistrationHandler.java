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

package org.idp.server.core.openid.clientinstance.registration.handler;

import org.idp.server.core.openid.clientinstance.ClientInstance;
import org.idp.server.core.openid.clientinstance.registration.ClientInstanceRegistrationChallenge;
import org.idp.server.core.openid.clientinstance.registration.ClientInstanceRegistrationChallengeIssuer;
import org.idp.server.core.openid.clientinstance.registration.ClientInstanceRegistrationChallengeRepository;
import org.idp.server.core.openid.clientinstance.registration.ClientInstanceRegistrationService;
import org.idp.server.core.openid.clientinstance.registration.handler.io.ClientInstanceChallengeRequest;
import org.idp.server.core.openid.clientinstance.registration.handler.io.ClientInstanceRegisterRequest;
import org.idp.server.core.openid.clientinstance.registration.handler.io.ClientInstanceRegistrationResponse;
import org.idp.server.core.openid.clientinstance.registration.verifier.ClientInstanceRegistrationPolicyVerifier;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Client Instance registration, in two steps.
 *
 * <p>Both endpoints are unauthenticated. What authorizes a registration is a server issued ticket
 * plus the platform attestation bound to it, so the two steps carry different weight:
 *
 * <ul>
 *   <li>{@link #handleChallenge} decides what may be registered — which client, which device, which
 *       instance identifier — and keeps that decision server-side as the ticket
 *   <li>{@link #handleRegister} only checks that the ticket is valid and that the evidence is bound
 *       to it ({@link ClientInstanceRegistrationService})
 * </ul>
 */
public class ClientInstanceRegistrationHandler {

  static final int CHALLENGE_EXPIRES_IN_SECONDS = 300;

  ClientConfigurationQueryRepository clientConfigurationQueryRepository;
  ClientInstanceRegistrationChallengeRepository challengeRepository;
  ClientInstanceRegistrationChallengeIssuer challengeIssuer;
  ClientInstanceRegistrationPolicyVerifier policyVerifier;
  ClientInstanceRegistrationService registrationService;

  public ClientInstanceRegistrationHandler(
      ClientConfigurationQueryRepository clientConfigurationQueryRepository,
      ClientInstanceRegistrationChallengeRepository challengeRepository,
      ClientInstanceRegistrationChallengeIssuer challengeIssuer,
      ClientInstanceRegistrationPolicyVerifier policyVerifier,
      ClientInstanceRegistrationService registrationService) {
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
    this.challengeRepository = challengeRepository;
    this.challengeIssuer = challengeIssuer;
    this.policyVerifier = policyVerifier;
    this.registrationService = registrationService;
  }

  public ClientInstanceRegistrationResponse handleChallenge(
      Tenant tenant, ClientInstanceChallengeRequest request) {

    RequestedClientId requestedClientId = request.requestedClientId();
    String deviceId = request.deviceId();

    ClientConfiguration clientConfiguration =
        clientConfigurationQueryRepository.get(tenant, requestedClientId);
    policyVerifier.verify(tenant, clientConfiguration, requestedClientId, deviceId);

    ClientInstanceRegistrationChallenge challenge =
        challengeIssuer.issue(tenant, requestedClientId, deviceId, CHALLENGE_EXPIRES_IN_SECONDS);
    challengeRepository.register(tenant, challenge);

    return ClientInstanceRegistrationResponse.challengeIssued(
        challenge, CHALLENGE_EXPIRES_IN_SECONDS);
  }

  public ClientInstanceRegistrationResponse handleRegister(
      Tenant tenant, ClientInstanceRegisterRequest request) {

    ClientInstance clientInstance =
        registrationService.register(
            tenant, request.challenge(), request.instanceKey(), request.platformEvidence());

    return ClientInstanceRegistrationResponse.registered(clientInstance);
  }
}
