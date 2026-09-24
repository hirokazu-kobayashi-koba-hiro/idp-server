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

package org.idp.server.core.openid.oauth.clientattestation.challenge.handler;

import org.idp.server.core.openid.oauth.clientattestation.challenge.ClientAttestationChallenge;
import org.idp.server.core.openid.oauth.clientattestation.challenge.ClientAttestationChallengeIssuer;
import org.idp.server.core.openid.oauth.clientattestation.challenge.ClientAttestationChallengeRepository;
import org.idp.server.core.openid.oauth.clientattestation.challenge.handler.io.ClientAttestationChallengeResponse;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Issues a Challenge for draft-ietf-oauth-attestation-based-client-auth-11 Section 6.3.
 *
 * <p>The lifetime is the tenant's, not a constant: a Challenge stands in for the session it is used
 * across, and how long that is depends on the flow the tenant runs.
 */
public class ClientAttestationChallengeHandler {

  AuthorizationServerConfigurationQueryRepository authorizationServerConfigurationQueryRepository;
  ClientAttestationChallengeRepository challengeRepository;
  ClientAttestationChallengeIssuer challengeIssuer;

  public ClientAttestationChallengeHandler(
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository,
      ClientAttestationChallengeRepository challengeRepository,
      ClientAttestationChallengeIssuer challengeIssuer) {
    this.authorizationServerConfigurationQueryRepository =
        authorizationServerConfigurationQueryRepository;
    this.challengeRepository = challengeRepository;
    this.challengeIssuer = challengeIssuer;
  }

  public ClientAttestationChallengeResponse handle(Tenant tenant) {

    AuthorizationServerConfiguration authorizationServerConfiguration =
        authorizationServerConfigurationQueryRepository.get(tenant);

    ClientAttestationChallenge challenge =
        challengeIssuer.issue(
            authorizationServerConfiguration.clientAttestationChallengeDuration());
    challengeRepository.register(tenant, challenge);

    return ClientAttestationChallengeResponse.ok(challenge);
  }
}
