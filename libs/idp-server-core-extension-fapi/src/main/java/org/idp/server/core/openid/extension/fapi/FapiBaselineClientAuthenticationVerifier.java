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

import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientAuthenticationPublicKey;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientCredentials;
import org.idp.server.core.openid.oauth.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.type.oauth.ClientAuthenticationType;
import org.idp.server.core.openid.oauth.type.oauth.ClientSecret;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;

/**
 * FAPI 1.0 Baseline 5.2.2: client authentication requirements, applied to every request the client
 * authenticates (pushed authorization request, authorization code and refresh token requests).
 *
 * <p>A violation is a failed client authentication and is reported as {@code invalid_client} (RFC
 * 6749 Section 5.2) through {@link ClientUnAuthorizedException}.
 */
public class FapiBaselineClientAuthenticationVerifier {

  static final String PROFILE = "FAPI Baseline profile";

  public void verify(ClientConfiguration clientConfiguration, ClientCredentials clientCredentials) {
    throwExceptionIfClientSecretPostOrClientSecretBasic(clientConfiguration);
    throwExceptionIfClientSecretTooShort(clientConfiguration, clientCredentials);
    throwExceptionIfKeyTooSmall(clientConfiguration, clientCredentials, PROFILE);
  }

  /**
   * 5.2.2-4: shall authenticate the confidential client using one of the following methods: Mutual
   * TLS for OAuth Client Authentication as specified in Section 2 of MTLS, or client_secret_jwt or
   * private_key_jwt as specified in Section 9 of OIDC;
   */
  void throwExceptionIfClientSecretPostOrClientSecretBasic(
      ClientConfiguration clientConfiguration) {
    ClientAuthenticationType type = clientConfiguration.clientAuthenticationType();
    if (type.isClientSecretBasic() || type.isClientSecretPost()) {
      throw unauthorized(
          clientConfiguration, String.format("When %s, %s MUST not be used", PROFILE, type.name()));
    }
  }

  /**
   * 5.2.2-3: shall provide a client secret that adheres to the requirements in Section 16.19 of
   * OIDC if a symmetric key is used;
   *
   * <p>OIDC 16.19: client_secret values MUST also contain at least the minimum of number of octets
   * required for MAC keys for the particular algorithm used (32 octets for HS256, 48 for HS384, 64
   * for HS512).
   */
  void throwExceptionIfClientSecretTooShort(
      ClientConfiguration clientConfiguration, ClientCredentials clientCredentials) {
    if (!clientConfiguration.clientAuthenticationType().isClientSecretJwt()) {
      return;
    }
    int requiredOctets = requiredSecretOctets(clientCredentials.clientAssertionJwt().algorithm());
    ClientSecret clientSecret = clientConfiguration.clientSecret();
    if (clientSecret.octetsSize() < requiredOctets) {
      throw unauthorized(
          clientConfiguration,
          String.format(
              "When %s, the client_secret value MUST contain at least %d octets",
              PROFILE, requiredOctets));
    }
  }

  int requiredSecretOctets(String algorithm) {
    if ("HS512".equals(algorithm)) {
      return 64;
    }
    if ("HS384".equals(algorithm)) {
      return 48;
    }
    return 32;
  }

  /**
   * 5.2.2-5: shall require and use a key of size 2048 bits or larger for RSA algorithms;
   *
   * <p>5.2.2-6: shall require and use a key of size 160 bits or larger for elliptic curve
   * algorithms;
   */
  void throwExceptionIfKeyTooSmall(
      ClientConfiguration clientConfiguration,
      ClientCredentials clientCredentials,
      String profile) {
    if (!clientConfiguration.clientAuthenticationType().isPrivateKeyJwt()) {
      return;
    }
    ClientAuthenticationPublicKey publicKey = clientCredentials.clientAuthenticationPublicKey();
    if (publicKey.isRsa() && publicKey.size() < 2048) {
      throw unauthorized(
          clientConfiguration,
          String.format(
              "When %s, shall require and use a key of size 2048 bits or larger for RSA algorithms. Current key size: %d bits",
              profile, publicKey.size()));
    }
    if (publicKey.isEc() && publicKey.size() < 160) {
      throw unauthorized(
          clientConfiguration,
          String.format(
              "When %s, shall require and use a key of size 160 bits or larger for elliptic curve algorithms. Current key size: %d bits",
              profile, publicKey.size()));
    }
  }

  static ClientUnAuthorizedException unauthorized(
      ClientConfiguration clientConfiguration, String reason) {
    return new ClientUnAuthorizedException(
        clientConfiguration.clientAuthenticationType().name(),
        new RequestedClientId(clientConfiguration.clientIdValue()),
        reason);
  }
}
