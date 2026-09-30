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
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientCredentials;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.type.oauth.ClientAuthenticationType;

/**
 * FAPI 1.0 Advanced: client authentication requirements, applied to every request the client
 * authenticates (pushed authorization request, authorization code and refresh token requests).
 *
 * <p>Advanced 5.2.2 carries over the Baseline provisions (Part 2 5.2.2: "shall support the
 * provisions specified in clause 5.2.2 of Financial-grade API Security Profile 1.0 - Part 1:
 * Baseline"), replacing Baseline 5.2.2-4 with 5.2.2-14.
 */
public class FapiAdvanceClientAuthenticationVerifier {

  static final String PROFILE = "FAPI Advance profile";

  /** 8.6: shall use PS256 or ES256 algorithms. */
  static final Set<String> ALLOWED_CLIENT_ASSERTION_ALGORITHMS = Set.of("PS256", "ES256");

  FapiBaselineClientAuthenticationVerifier baselineVerifier =
      new FapiBaselineClientAuthenticationVerifier();

  public void verify(ClientConfiguration clientConfiguration, ClientCredentials clientCredentials) {
    throwExceptionIfClientSecretPostOrClientSecretBasicOrClientSecretJwt(clientConfiguration);
    throwExceptionIfPublicClient(clientConfiguration);
    throwExceptionIfInvalidSigningAlgorithm(clientConfiguration, clientCredentials);
    baselineVerifier.throwExceptionIfKeyTooSmall(clientConfiguration, clientCredentials, PROFILE);
  }

  /**
   * 5.2.2-14: shall authenticate the confidential client using one of the following methods (this
   * overrides FAPI Security Profile 1.0 - Part 1: Baseline clause 5.2.2-4): tls_client_auth or
   * self_signed_tls_client_auth as specified in section 2 of MTLS, or private_key_jwt as specified
   * in section 9 of OIDC;
   */
  void throwExceptionIfClientSecretPostOrClientSecretBasicOrClientSecretJwt(
      ClientConfiguration clientConfiguration) {
    ClientAuthenticationType type = clientConfiguration.clientAuthenticationType();
    if (type.isClientSecretBasic() || type.isClientSecretPost() || type.isClientSecretJwt()) {
      throw unauthorized(
          clientConfiguration, String.format("When %s, %s MUST not be used", PROFILE, type.name()));
    }
  }

  /** 5.2.2-16: shall not support public clients; */
  void throwExceptionIfPublicClient(ClientConfiguration clientConfiguration) {
    if (clientConfiguration.clientAuthenticationType().isNone()) {
      throw unauthorized(
          clientConfiguration, String.format("When %s, shall not support public clients", PROFILE));
    }
  }

  /**
   * 8.6-1: shall use PS256 or ES256 algorithms;
   *
   * <p>8.6-2: should not use algorithms that use RSASSA-PKCS1-v1_5 (e.g. RS256);
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
              "When %s, client assertion signing algorithm must be PS256 or ES256 (Section 8.6). Current algorithm: %s",
              PROFILE, algorithm));
    }
  }
}
