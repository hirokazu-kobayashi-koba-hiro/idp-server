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
package org.idp.server.core.openid.extension.fapi;

import static org.idp.server.core.openid.extension.fapi.FapiBaselineClientAuthenticationVerifier.unauthorized;

import java.util.Set;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientAssertionJwt;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientAuthenticationPublicKey;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientCredentials;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.type.oauth.ClientAuthenticationType;

/**
 * FAPI 2.0 Security Profile: client authentication requirements, applied to every request the
 * client authenticates (pushed authorization request, authorization code and refresh token
 * requests).
 */
public class Fapi20ClientAuthenticationVerifier {

  static final String PROFILE = "FAPI 2.0 Security Profile";

  /** 5.4.1: signing algorithms permitted on client assertions. */
  static final Set<String> ALLOWED_CLIENT_ASSERTION_ALGORITHMS =
      Set.of("PS256", "PS384", "PS512", "ES256", "ES384", "ES512", "EdDSA");

  public void verify(
      AuthorizationServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration,
      ClientCredentials clientCredentials) {
    throwExceptionIfWeakClientAuthentication(clientConfiguration);
    throwExceptionIfInvalidSigningAlgorithm(clientConfiguration, clientCredentials);
    throwExceptionIfClientAssertionAudIsNotIssuer(
        serverConfiguration, clientConfiguration, clientCredentials);
  }

  /**
   * 5.3.2.1: shall only support confidential clients as defined in [RFC6749], and shall
   * authenticate clients using one of the following methods: MTLS as specified in Section 2 of
   * [RFC8705], or private_key_jwt as specified in Section 9 of [OIDC].
   *
   * <p>{@code client_secret_*} and public clients are refused; {@code attest_jwt_client_auth} is
   * left to the profiles built on FAPI 2.0 (HAIP).
   */
  void throwExceptionIfWeakClientAuthentication(ClientConfiguration clientConfiguration) {
    ClientAuthenticationType type = clientConfiguration.clientAuthenticationType();
    if (type.isClientSecretBasic() || type.isClientSecretPost() || type.isClientSecretJwt()) {
      throw unauthorized(
          clientConfiguration,
          String.format(
              "When %s, client authentication MUST be one of mTLS or private_key_jwt. %s is not allowed",
              PROFILE, type.name()));
    }
    if (type.isNone()) {
      throw unauthorized(
          clientConfiguration, String.format("When %s, public clients are not allowed", PROFILE));
    }
  }

  /**
   * 5.4.1: client assertions are signed with PS256 / PS384 / PS512, ES256 / ES384 / ES512 or EdDSA,
   * with RSA keys of 2048 bits or larger and elliptic curve keys of 224 bits or larger.
   */
  void throwExceptionIfInvalidSigningAlgorithm(
      ClientConfiguration clientConfiguration, ClientCredentials clientCredentials) {
    if (!clientConfiguration.clientAuthenticationType().isPrivateKeyJwt()) {
      return;
    }
    String algorithm = clientCredentials.clientAssertionJwt().algorithm();
    if (!ALLOWED_CLIENT_ASSERTION_ALGORITHMS.contains(algorithm)) {
      throw unauthorized(
          clientConfiguration,
          String.format(
              "When %s (5.4.1), client assertion signing algorithm must be one of %s. Current algorithm: %s",
              PROFILE, ALLOWED_CLIENT_ASSERTION_ALGORITHMS, algorithm));
    }
    ClientAuthenticationPublicKey publicKey = clientCredentials.clientAuthenticationPublicKey();
    int keySize = publicKey.size();
    if (algorithm.startsWith("PS") && keySize < 2048) {
      throw unauthorized(
          clientConfiguration,
          String.format(
              "When %s, RSA key size must be 2048 bits or larger. Current key size: %d bits",
              PROFILE, keySize));
    }
    if (algorithm.startsWith("ES") && keySize < 224) {
      throw unauthorized(
          clientConfiguration,
          String.format(
              "When %s, elliptic curve key size must be 224 bits or larger. Current key size: %d bits",
              PROFILE, keySize));
    }
    // Ed25519 (256 bits) and Ed448 (456 bits) both meet this; the check guards against anything
    // smaller.
    if ("EdDSA".equals(algorithm) && keySize < 256) {
      throw unauthorized(
          clientConfiguration,
          String.format(
              "When %s, EdDSA key size must be 256 bits or larger. Current key size: %d bits",
              PROFILE, keySize));
    }
  }

  /**
   * 5.3.2.1-8: shall only accept its issuer identifier value (as defined in [RFC8414]) as a string
   * in the aud claim received in client authentication assertions;
   */
  void throwExceptionIfClientAssertionAudIsNotIssuer(
      AuthorizationServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration,
      ClientCredentials clientCredentials) {
    if (!clientConfiguration.clientAuthenticationType().isPrivateKeyJwt()) {
      return;
    }
    ClientAssertionJwt clientAssertionJwt = clientCredentials.clientAssertionJwt();
    if (clientAssertionJwt.isAudArray()) {
      throw unauthorized(
          clientConfiguration,
          String.format(
              "When %s (5.3.2.1-8), client assertion aud claim must be a string, not an array",
              PROFILE));
    }
    Object aud = clientAssertionJwt.getFromRawPayload("aud");
    String issuer = serverConfiguration.tokenIssuer().value();
    if (!issuer.equals(aud)) {
      throw unauthorized(
          clientConfiguration,
          String.format(
              "When %s (5.3.2.1-8), client assertion aud must be the AS issuer identifier (%s). Received: %s",
              PROFILE, issuer, aud));
    }
  }
}
