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
package org.idp.server.core.openid.token.verifier;

import org.idp.server.core.openid.oauth.clientauthenticator.mtls.ClientCertification;
import org.idp.server.core.openid.oauth.clientauthenticator.mtls.ClientCertificationThumbprint;
import org.idp.server.core.openid.oauth.clientauthenticator.mtls.ClientCertificationThumbprintCalculator;
import org.idp.server.core.openid.oauth.dpop.AccessTokenHashCalculator;
import org.idp.server.core.openid.oauth.dpop.DPoPProof;
import org.idp.server.core.openid.oauth.dpop.DPoPProofInvalidException;
import org.idp.server.core.openid.oauth.dpop.DPoPProofVerifiedResult;
import org.idp.server.core.openid.oauth.dpop.DPoPProofVerifier;
import org.idp.server.core.openid.oauth.type.mtls.ClientCert;
import org.idp.server.core.openid.token.AccessToken;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.core.openid.token.tokenintrospection.exception.TokenInvalidException;
import org.idp.server.platform.x509.X509CertInvalidException;

/**
 * Checks that a sender-constrained access token is presented by its holder, at a protected resource
 * of this server (the UserInfo Endpoint, the Credential Endpoint).
 *
 * <ul>
 *   <li>RFC 8705 Section 3: a certificate-bound token needs the same client certificate.
 *   <li>RFC 9449 Section 7.1: a DPoP-bound token needs a DPoP proof for this request, whose key is
 *       the one the token is bound to and whose {@code ath} is the hash of the token.
 * </ul>
 *
 * <p>An unbound token passes: whether a resource accepts one is the resource's decision.
 */
public class AccessTokenSenderConstraintVerifier {

  OAuthToken oAuthToken;
  ClientCert clientCert;
  DPoPProof dpopProof;
  String httpMethod;
  String httpUri;

  public AccessTokenSenderConstraintVerifier(
      OAuthToken oAuthToken,
      ClientCert clientCert,
      DPoPProof dpopProof,
      String httpMethod,
      String httpUri) {
    this.oAuthToken = oAuthToken;
    this.clientCert = clientCert;
    this.dpopProof = dpopProof;
    this.httpMethod = httpMethod;
    this.httpUri = httpUri;
  }

  public void verify() {
    throwExceptionIfUnMatchClientCert();
    throwExceptionIfUnMatchDPoPProof();
  }

  public void throwExceptionIfUnMatchClientCert() {
    if (!oAuthToken.hasClientCertification()) {
      return;
    }
    if (clientCert == null || !clientCert.exists()) {
      throw new TokenInvalidException(
          "access token is sender constrained, but mtls client cert does not exists");
    }
    try {
      ClientCertification clientCertification = ClientCertification.parse(clientCert.plainValue());
      ClientCertificationThumbprintCalculator calculator =
          new ClientCertificationThumbprintCalculator(clientCertification);
      ClientCertificationThumbprint thumbprint = calculator.calculate();
      AccessToken accessToken = oAuthToken.accessToken();
      if (!accessToken.matchThumbprint(thumbprint)) {
        throw new TokenInvalidException("access token and mtls client cert is unmatch");
      }
    } catch (X509CertInvalidException e) {
      throw new TokenInvalidException(
          "access token is sender constrained, but mtls client cert is invalid format", e);
    }
  }

  public void throwExceptionIfUnMatchDPoPProof() {
    AccessToken accessToken = oAuthToken.accessToken();
    if (!accessToken.hasDPoPBinding()) {
      return;
    }
    if (dpopProof == null || !dpopProof.exists()) {
      throw new TokenInvalidException(
          "access token is DPoP-bound, but DPoP proof header is missing");
    }
    try {
      String accessTokenValue = oAuthToken.accessTokenEntity().value();
      String ath = new AccessTokenHashCalculator(accessTokenValue).calculate();
      DPoPProofVerifier verifier = new DPoPProofVerifier();
      DPoPProofVerifiedResult result = verifier.verify(dpopProof, httpMethod, httpUri, ath);
      if (!accessToken.matchJwkThumbprint(result.jwkThumbprint())) {
        throw new TokenInvalidException(
            "DPoP proof JWK thumbprint does not match the access token binding");
      }
    } catch (DPoPProofInvalidException e) {
      throw new TokenInvalidException("DPoP proof validation failed: " + e.getMessage());
    }
  }
}
