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

package org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials;

import static org.junit.jupiter.api.Assertions.*;

import org.idp.server.core.openid.oauth.clientauthenticator.mtls.ClientCertification;
import org.idp.server.core.openid.oauth.type.oauth.ClientAuthenticationType;
import org.idp.server.core.openid.oauth.type.oauth.ClientSecret;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.junit.jupiter.api.Test;

/**
 * The no-argument constructor is a real state, not a placeholder.
 *
 * <p>A public client reaches token issuance with {@code new ClientCredentials()}, every field null.
 * Anything that asks this object a question has to answer it from there too — reading {@code
 * clientAuthenticationType()} directly threw a {@code NullPointerException} on that path, which
 * surfaced as {@code server_error} at the token endpoint rather than as anything diagnosable.
 */
class ClientCredentialsTest {

  @Test
  void answersWithoutAnAuthenticationTypeRatherThanThrowing() {
    ClientCredentials publicClient = new ClientCredentials();

    assertFalse(publicClient.isAttestJwtClientAuth());
    assertFalse(publicClient.isTlsClientAuthOrSelfSignedTlsClientAuth());
    assertFalse(publicClient.hasClientCertification());
  }

  @Test
  void recognisesAttestJwtClientAuth() {
    ClientCredentials credentials =
        new ClientCredentials(
            new RequestedClientId("client"),
            ClientAuthenticationType.attest_jwt_client_auth,
            new ClientSecret(),
            new ClientAuthenticationPublicKey(),
            new ClientAssertionJwt(),
            new ClientCertification());

    assertTrue(credentials.isAttestJwtClientAuth());
  }

  @Test
  void doesNotMistakeAnotherAuthenticationMethodForIt() {
    ClientCredentials credentials =
        new ClientCredentials(
            new RequestedClientId("client"),
            ClientAuthenticationType.private_key_jwt,
            new ClientSecret(),
            new ClientAuthenticationPublicKey(),
            new ClientAssertionJwt(),
            new ClientCertification());

    assertFalse(credentials.isAttestJwtClientAuth());
  }
}
