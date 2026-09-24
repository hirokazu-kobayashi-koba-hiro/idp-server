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

package org.idp.server.core.openid.clientinstance.registration.verifier;

import org.idp.server.core.openid.clientinstance.ClientInstanceRegistrationPolicy;
import org.idp.server.core.openid.clientinstance.registration.ClientInstanceRegistrationException;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;

/**
 * Decides whether a registration ticket may be issued for a client.
 *
 * <p>Who the instance belongs to is not known at issuance: the user is identified by the ID token
 * presented with the registration itself (see {@link ClientInstanceRegistrationIdTokenVerifier}).
 * What can be decided here is whether the client takes part in registration at all.
 */
public class ClientInstanceRegistrationPolicyVerifier {

  public void verify(ClientConfiguration clientConfiguration, RequestedClientId requestedClientId) {
    throwExceptionIfClientDoesNotUseAttestation(clientConfiguration, requestedClientId);
    throwExceptionIfPolicyIsNotUserBound(clientConfiguration);
  }

  private void throwExceptionIfClientDoesNotUseAttestation(
      ClientConfiguration clientConfiguration, RequestedClientId requestedClientId) {
    if (!clientConfiguration.clientAuthenticationType().isAttestJwtClientAuth()) {
      throw new ClientInstanceRegistrationException(
          "client does not use attest_jwt_client_auth: " + requestedClientId.value());
    }
  }

  private void throwExceptionIfPolicyIsNotUserBound(ClientConfiguration clientConfiguration) {
    ClientInstanceRegistrationPolicy policy =
        clientConfiguration.clientInstanceRegistrationPolicy();
    if (!policy.isUserBound()) {
      throw new ClientInstanceRegistrationException(
          "client_instance_registration_policy is not configured or has an unknown value");
    }
  }
}
