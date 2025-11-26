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

import org.idp.server.core.openid.oauth.clientauthenticator.mtls.ClientCertification;
import org.idp.server.core.openid.oauth.clientauthenticator.mtls.ClientCertificationThumbprint;
import org.idp.server.core.openid.oauth.clientauthenticator.mtls.ClientCertificationThumbprintCalculator;
import org.idp.server.core.openid.oauth.type.mtls.ClientCert;
import org.idp.server.core.openid.token.AccessToken;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.core.openid.token.tokenintrospection.exception.TokenCertificationBindingInvalidException;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.x509.X509CertInvalidException;

/**
 * Verifies certificate binding for sender-constrained access tokens per RFC 8705.
 *
 * <p>This verifier implements RFC 8705 Section 3: Certificate-Bound Access Tokens validation. When
 * an access token contains a confirmation claim (cnf) with x5t#S256 thumbprint, this verifier
 * ensures the presented client certificate matches the bound certificate.
 *
 * <h2>Verification Process</h2>
 *
 * <ol>
 *   <li>Check if token has client certification (cnf claim)
 *   <li>Verify client certificate was presented in the request
 *   <li>Calculate SHA-256 thumbprint of presented certificate
 *   <li>Compare with thumbprint in token's cnf claim
 * </ol>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * ClientCert clientCert = new ClientCert(certString);
 * OAuthToken oAuthToken = tokenRepository.find(tenant, accessToken);
 *
 * CertificateBindingVerifier verifier = new CertificateBindingVerifier();
 * verifier.verify(clientCert, oAuthToken);  // throws if mismatch
 * }</pre>
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc8705.html#section-3">RFC 8705 Section 3:
 *     Certificate-Bound Access Tokens</a>
 * @see TokenCertificationBindingInvalidException
 * @since 1.0.0
 */
public class CertificateBindingVerifier {

  LoggerWrapper log = LoggerWrapper.getLogger(CertificateBindingVerifier.class);

  /**
   * Verifies sender-constrained access token certificate binding.
   *
   * @param clientCert the client certificate presented in the request
   * @param oAuthToken the OAuth token to verify
   * @throws TokenCertificationBindingInvalidException if certificate binding validation fails
   */
  public void verify(ClientCert clientCert, OAuthToken oAuthToken) {
    // Skip verification if token is not certificate-bound
    if (!oAuthToken.hasClientCertification()) {
      log.debug("Token is not certificate-bound, skipping verification");
      return;
    }

    log.debug("Token is certificate-bound, verifying client certificate");

    // RFC 8705 Section 3: Certificate required for bound tokens
    if (!clientCert.exists()) {
      log.warn("Certificate-bound token presented without client certificate");
      throw new TokenCertificationBindingInvalidException(
          "Sender-constrained access token requires mTLS client certificate, but none was provided.");
    }

    try {
      ClientCertification clientCertification = ClientCertification.parse(clientCert.plainValue());
      ClientCertificationThumbprint thumbprint =
          new ClientCertificationThumbprintCalculator(clientCertification).calculate();

      AccessToken accessToken = oAuthToken.accessToken();

      log.debug(
          "Certificate thumbprint verification: expected={}, actual={}",
          accessToken.clientCertificationThumbprint(),
          thumbprint.value());

      if (!accessToken.matchThumbprint(thumbprint)) {
        log.warn(
            "Certificate thumbprint mismatch: expected={}, actual={}",
            accessToken.clientCertificationThumbprint(),
            thumbprint.value());
        throw new TokenCertificationBindingInvalidException(
            "mTLS client certificate thumbprint does not match the sender-constrained access token.");
      }

      log.debug("Certificate binding verification succeeded");

    } catch (X509CertInvalidException e) {
      log.warn("Invalid client certificate format: {}", e.getMessage(), e);
      throw new TokenCertificationBindingInvalidException(
          String.format(
              "Invalid mTLS client certificate format for sender-constrained access token: %s",
              e.getMessage()));
    }
  }
}
