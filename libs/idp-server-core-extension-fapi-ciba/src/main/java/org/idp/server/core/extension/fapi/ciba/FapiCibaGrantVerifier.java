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

package org.idp.server.core.extension.fapi.ciba;

import org.idp.server.core.extension.ciba.CibaProfile;
import org.idp.server.core.extension.ciba.grant.CibaGrant;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.extension.ciba.token.CibaGrantBaseVerifier;
import org.idp.server.core.extension.ciba.token.CibaGrantVerifierInterface;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientAssertionJwt;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientAuthenticationPublicKey;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientCredentials;
import org.idp.server.core.openid.oauth.type.oauth.ClientAuthenticationType;
import org.idp.server.core.openid.token.TokenRequestContext;
import org.idp.server.core.openid.token.exception.TokenBadRequestException;

/**
 * FapiCibaGrantVerifier
 *
 * <p>FAPI-CIBA profile specific grant verifier for the Token Endpoint. Extends the base CIBA
 * verification with additional security requirements mandated by the FAPI-CIBA specification.
 *
 * <p>Additional verifications include:
 *
 * <ul>
 *   <li>Sender-constrained access tokens (mTLS certificate binding required)
 *   <li>Confidential client authentication methods only (tls_client_auth,
 *       self_signed_tls_client_auth, private_key_jwt)
 *   <li>Signing algorithm restrictions (PS256 or ES256 only, RS256 prohibited)
 *   <li>Key size requirements (RSA 2048+ bits, EC 160+ bits)
 * </ul>
 *
 * @see CibaGrantBaseVerifier
 * @see <a href="https://openid.net/specs/openid-financial-api-ciba-ID1.html">FAPI-CIBA Profile</a>
 * @see <a
 *     href="https://openid.net/specs/openid-financial-api-ciba-ID1.html#rfc.section.5.2.2">FAPI-CIBA
 *     5.2.2 - Authorization Server</a>
 */
public class FapiCibaGrantVerifier implements CibaGrantVerifierInterface {

  CibaGrantBaseVerifier baseVerifier = new CibaGrantBaseVerifier();

  public FapiCibaGrantVerifier() {}

  @Override
  public CibaProfile profile() {
    return CibaProfile.FAPI_CIBA;
  }

  @Override
  public void verify(
      TokenRequestContext context,
      BackchannelAuthenticationRequest request,
      CibaGrant cibaGrant,
      ClientCredentials clientCredentials) {
    baseVerifier.verify(context, request, cibaGrant, clientCredentials);
    throwExceptionIfFapiCibaAndCertificateBoundRequiredButMissing(context, clientCredentials);
    throwExceptionIfClientSecretPostOrClientSecretBasicOrClientSecretJwtOrPublicClient(context);
    throwExceptionIfInvalidSigningAlgorithmForClientAssertion(context, clientCredentials);
  }

  /**
   * Sender-Constrained Access Token Requirement (FAPI-CIBA 5.2.2)
   *
   * <p>FAPI-CIBA requires all access tokens to be sender-constrained using mTLS certificate
   * binding. This ensures that access tokens can only be used by the client that obtained them.
   *
   * @throws TokenBadRequestException if client certificate is not present
   */
  void throwExceptionIfFapiCibaAndCertificateBoundRequiredButMissing(
      TokenRequestContext context, ClientCredentials clientCredentials) {
    if (!clientCredentials.hasClientCertification()) {
      throw new TokenBadRequestException(
          "invalid_request", "When FAPI-CIBA profile MUST be sender constrained access_token");
    }
  }

  /**
   * shall authenticate the confidential client using one of the following methods (this overrides
   * FAPI Security Profile 1.0 - Part 1: Baseline clause 5.2.2-4): tls_client_auth or
   * self_signed_tls_client_auth as specified in section 2 of MTLS, or private_key_jwt as specified
   * in section 9 of OIDC;
   *
   * <p>shall not support public clients;
   */
  void throwExceptionIfClientSecretPostOrClientSecretBasicOrClientSecretJwtOrPublicClient(
      TokenRequestContext tokenRequestContext) {
    ClientAuthenticationType clientAuthenticationType =
        tokenRequestContext.clientAuthenticationType();
    if (clientAuthenticationType.isClientSecretBasic()) {
      throw new TokenBadRequestException(
          "unauthorized_client", "When FAPI Baseline profile, client_secret_basic MUST not used");
    }
    if (clientAuthenticationType.isClientSecretPost()) {
      throw new TokenBadRequestException(
          "unauthorized_client", "When FAPI Baseline profile, client_secret_post MUST not used");
    }
    if (clientAuthenticationType.isClientSecretJwt()) {
      throw new TokenBadRequestException(
          "unauthorized_client", "When FAPI Baseline profile, client_secret_jwt MUST not used");
    }
    if (clientAuthenticationType.isNone()) {
      throw new TokenBadRequestException(
          "unauthorized_client", "When FAPI Baseline profile, shall not support public clients");
    }
  }

  /**
   * 5.2.2.3 Signing Algorithm Restrictions (FAPI CIBA 7.10)
   *
   * <p>shall use PS256 or ES256 algorithms
   *
   * <p>should not use algorithms that use RSASSA-PKCS1-v1_5 (e.g. RS256)
   *
   * <p>shall not use none
   *
   * <p>5.2.2.4 Key Size Requirements (FAPI Part 1 5.2.2-5, 5.2.2-6)
   *
   * <p>shall require and use a key of size 2048 bits or larger for RSA algorithms (PS256)
   *
   * <p>shall require and use a key of size 160 bits or larger for elliptic curve algorithms (ES256)
   */
  void throwExceptionIfInvalidSigningAlgorithmForClientAssertion(
      TokenRequestContext context, ClientCredentials clientCredentials) {
    if (!context.clientAuthenticationType().isPrivateKeyJwt()) {
      return;
    }

    ClientAssertionJwt clientAssertionJwt = clientCredentials.clientAssertionJwt();
    String algorithm = clientAssertionJwt.algorithm();

    // FAPI CIBA 7.10: shall use PS256 or ES256 algorithms
    if (!"PS256".equals(algorithm) && !"ES256".equals(algorithm)) {
      throw new TokenBadRequestException(
          "invalid_request",
          String.format(
              "FAPI CIBA Profile requires signing algorithm to be PS256 or ES256. Current algorithm: %s",
              algorithm));
    }

    // FAPI Part 1 5.2.2-5, 5.2.2-6: Key size requirements
    ClientAuthenticationPublicKey clientAuthenticationPublicKey =
        clientCredentials.clientAuthenticationPublicKey();
    int keySize = clientAuthenticationPublicKey.size();

    if ("PS256".equals(algorithm) && keySize < 2048) {
      // RSA algorithms require 2048 bits or larger
      throw new TokenBadRequestException(
          "invalid_request",
          String.format(
              "FAPI CIBA Profile requires RSA key size to be 2048 bits or larger. Current key size: %d bits",
              keySize));
    }

    if ("ES256".equals(algorithm) && keySize < 160) {
      // Elliptic curve algorithms require 160 bits or larger (ES256 uses 256 bits)
      throw new TokenBadRequestException(
          "invalid_request",
          String.format(
              "FAPI CIBA Profile requires elliptic curve key size to be 160 bits or larger. Current key size: %d bits",
              keySize));
    }
  }
}
