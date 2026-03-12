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

package org.idp.server.core.openid.token.tokenintrospection.verifier;

import org.idp.server.core.openid.oauth.dpop.AccessTokenHashCalculator;
import org.idp.server.core.openid.oauth.dpop.DPoPProof;
import org.idp.server.core.openid.oauth.dpop.DPoPProofInvalidException;
import org.idp.server.core.openid.oauth.dpop.DPoPProofVerifiedResult;
import org.idp.server.core.openid.oauth.dpop.DPoPProofVerifier;
import org.idp.server.core.openid.token.AccessToken;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.log.LoggerWrapper;

/**
 * Verifies DPoP binding for sender-constrained access tokens per RFC 9449.
 *
 * <p>This verifier implements RFC 9449 Section 7: Token Introspection for DPoP-bound tokens. When
 * an access token contains a confirmation claim (cnf) with jkt (JWK Thumbprint), this verifier
 * ensures the presented DPoP proof's public key matches the bound key.
 *
 * <h2>Verification Process</h2>
 *
 * <ol>
 *   <li>Check if token has DPoP binding (cnf.jkt claim)
 *   <li>Verify DPoP proof was presented in the request
 *   <li>Verify DPoP proof JWT (signature, claims, ath)
 *   <li>Compare JWK Thumbprint of proof's public key with token's jkt binding
 * </ol>
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9449.html#section-7">RFC 9449 Section 7</a>
 * @see CertificateBindingVerifier
 */
public class DPoPBindingVerifier {

  LoggerWrapper log = LoggerWrapper.getLogger(DPoPBindingVerifier.class);

  /**
   * Verifies DPoP-bound access token binding.
   *
   * @param dpopProof the DPoP proof JWT presented in the request
   * @param httpMethod the HTTP method of the introspection request
   * @param httpUri the HTTP URI of the introspection request
   * @param oAuthToken the OAuth token to verify
   * @throws DPoPProofInvalidException if DPoP binding validation fails
   */
  public void verify(
      DPoPProof dpopProof, String httpMethod, String httpUri, OAuthToken oAuthToken) {
    AccessToken accessToken = oAuthToken.accessToken();

    if (!accessToken.hasDPoPBinding()) {
      log.debug("Token is not DPoP-bound, skipping verification");
      return;
    }

    log.debug("Token is DPoP-bound, verifying DPoP proof");

    if (dpopProof == null || !dpopProof.exists()) {
      if (dpopProof != null && dpopProof.isPresentButEmpty()) {
        throw new DPoPProofInvalidException("DPoP header is present but empty");
      }
      log.warn("DPoP-bound token presented without DPoP proof");
      throw new DPoPProofInvalidException(
          "Sender-constrained access token requires DPoP proof, but none was provided.");
    }

    String accessTokenValue = oAuthToken.accessTokenEntity().value();
    String ath = new AccessTokenHashCalculator(accessTokenValue).calculate();
    DPoPProofVerifier verifier = new DPoPProofVerifier();
    DPoPProofVerifiedResult result = verifier.verify(dpopProof, httpMethod, httpUri, ath);

    log.debug(
        "DPoP thumbprint verification: expected={}, actual={}",
        accessToken.jwkThumbprint().value(),
        result.jwkThumbprint().value());

    if (!accessToken.matchJwkThumbprint(result.jwkThumbprint())) {
      log.warn(
          "DPoP thumbprint mismatch: expected={}, actual={}",
          accessToken.jwkThumbprint().value(),
          result.jwkThumbprint().value());
      throw new DPoPProofInvalidException(
          "DPoP proof JWK Thumbprint does not match the sender-constrained access token.");
    }

    log.debug("DPoP binding verification succeeded");
  }
}
