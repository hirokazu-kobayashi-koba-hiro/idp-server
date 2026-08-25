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

package org.idp.server.core.openid.oauth.clientauthenticator;

import org.idp.server.core.openid.oauth.clientattestation.ClientAttestationJwt;
import org.idp.server.core.openid.oauth.clientattestation.ClientAttestationPopJwt;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.type.mtls.ClientCert;
import org.idp.server.core.openid.oauth.type.oauth.ClientAuthenticationType;
import org.idp.server.core.openid.oauth.type.oauth.ClientSecretBasic;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface BackchannelRequestContext {

  BackchannelRequestParameters parameters();

  ClientSecretBasic clientSecretBasic();

  ClientCert clientCert();

  boolean hasClientSecretBasic();

  /**
   * Returns the Client Attestation JWT conveyed by the {@code OAuth-Client-Attestation} header.
   *
   * <p>Defaults to an empty value: contexts of endpoints that do not support Attestation-Based
   * Client Authentication simply leave this unimplemented, which makes {@code
   * attest_jwt_client_auth} fail there with a missing-attestation error.
   */
  default ClientAttestationJwt clientAttestationJwt() {
    return new ClientAttestationJwt();
  }

  /**
   * Returns the Client Attestation PoP JWT conveyed by the {@code OAuth-Client-Attestation-PoP}
   * header.
   */
  default ClientAttestationPopJwt clientAttestationPopJwt() {
    return new ClientAttestationPopJwt();
  }

  /**
   * Returns the tenant of the request. Defaults to {@code null} for contexts of endpoints that do
   * not need tenant-scoped lookups during client authentication.
   */
  default Tenant tenant() {
    return null;
  }

  /**
   * Whether the {@code challenge} claim of the Client Attestation PoP JWT is enforced
   * (draft-ietf-oauth-attestation-based-client-auth-10 Section 7.2 item 5).
   */
  default boolean isClientAttestationChallengeRequired() {
    return serverConfiguration().isClientAttestationChallengeRequired();
  }

  /** Lifetime in seconds of a Challenge issued while handling this request. */
  default int clientAttestationChallengeDuration() {
    return serverConfiguration().clientAttestationChallengeDuration();
  }

  AuthorizationServerConfiguration serverConfiguration();

  ClientConfiguration clientConfiguration();

  ClientAuthenticationType clientAuthenticationType();

  RequestedClientId requestedClientId();
}
