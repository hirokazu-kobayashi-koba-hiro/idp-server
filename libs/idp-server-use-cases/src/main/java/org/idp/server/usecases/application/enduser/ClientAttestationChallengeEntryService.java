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

import org.idp.server.core.openid.oauth.clientattestation.challenge.ClientAttestationChallenge;
import org.idp.server.core.openid.oauth.clientattestation.challenge.ClientAttestationChallengeApi;
import org.idp.server.core.openid.oauth.clientattestation.challenge.ClientAttestationChallengeIssuer;
import org.idp.server.core.openid.oauth.clientattestation.challenge.ClientAttestationChallengeRepository;
import org.idp.server.core.openid.oauth.clientattestation.challenge.ClientAttestationChallengeResponse;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Challenge endpoint of draft-ietf-oauth-attestation-based-client-auth-10 Section 6.1.
 *
 * <p>Unauthenticated: the endpoint returns an opaque nonce and nothing else, and the credential it
 * will be used with is only presented on the subsequent request.
 */
@Transaction
public class ClientAttestationChallengeEntryService implements ClientAttestationChallengeApi {

  TenantQueryRepository tenantQueryRepository;
  AuthorizationServerConfigurationQueryRepository authorizationServerConfigurationQueryRepository;
  ClientAttestationChallengeRepository challengeRepository;
  ClientAttestationChallengeIssuer challengeIssuer;

  public ClientAttestationChallengeEntryService(
      TenantQueryRepository tenantQueryRepository,
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository,
      ClientAttestationChallengeRepository challengeRepository) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.authorizationServerConfigurationQueryRepository =
        authorizationServerConfigurationQueryRepository;
    this.challengeRepository = challengeRepository;
    this.challengeIssuer = new ClientAttestationChallengeIssuer();
  }

  @Override
  public ClientAttestationChallengeResponse issue(
      TenantIdentifier tenantIdentifier, RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    AuthorizationServerConfiguration authorizationServerConfiguration =
        authorizationServerConfigurationQueryRepository.get(tenant);

    // Section 6.1 leaves the endpoint optional. A tenant that does not advertise
    // challenge_endpoint does not offer challenges, so it must not answer as if it did.
    if (!authorizationServerConfiguration.hasChallengeEndpoint()) {
      return ClientAttestationChallengeResponse.notFound();
    }

    ClientAttestationChallenge challenge =
        challengeIssuer.issue(
            authorizationServerConfiguration.clientAttestationChallengeDuration());
    challengeRepository.register(tenant, challenge);

    return ClientAttestationChallengeResponse.ok(challenge);
  }
}
