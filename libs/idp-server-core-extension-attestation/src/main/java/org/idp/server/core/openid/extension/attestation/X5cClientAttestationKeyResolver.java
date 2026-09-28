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

package org.idp.server.core.openid.extension.attestation;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.openid.oauth.clientauthenticator.BackchannelRequestContext;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.platform.jose.JsonWebKey;
import org.idp.server.platform.jose.JsonWebSignatureHeader;
import org.idp.server.platform.jose.JwkParser;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.x509.X509CertInvalidException;
import org.idp.server.platform.x509.X509CertificateChain;

/**
 * Resolves the Client Attester key from the {@code x5c} header of the Client Attestation JWT.
 *
 * <p>draft-ietf-oauth-attestation-based-client-auth-11 Section 10.8 describes this as one of the
 * shapes trust can take: the attestation carries the attester's certificate chain, and the server
 * validates it against a root it was configured with ({@code
 * client_attestation_trusted_root_certificates}).
 *
 * <p>What this buys over {@link StaticJwksClientAttestationKeyResolver} is whose problem key
 * rotation is. With a configured JWKS, an attester replacing its signing key means editing every
 * client that trusts it. With a chain, the root outlives the signing key and the replacement is
 * invisible here. Deployments built on a certificate hierarchy are shaped this way — the EUDI
 * Wallet's Wallet Instance Attestation is an {@code oauth-client-attestation+jwt} carrying the
 * Wallet Provider's certificate in {@code x5c}.
 *
 * <p>The chain is untrusted input. Only the root check makes any of it mean something: a chain that
 * merely parses was written by whoever sent it, and its leaf key would verify its own signature.
 * Validation therefore runs before the leaf key is handed back, and nothing is returned when it
 * fails — the JOSE layer would otherwise verify the signature against a key the attacker chose.
 */
public class X5cClientAttestationKeyResolver implements ClientAttestationKeyResolver {

  LoggerWrapper log = LoggerWrapper.getLogger(X5cClientAttestationKeyResolver.class);

  @Override
  public String resolveJwks(BackchannelRequestContext context, JsonWebSignatureHeader header) {
    ClientConfiguration clientConfiguration = context.clientConfiguration();

    if (!clientConfiguration.hasClientAttestationTrustedRootCertificates()) {
      log.warn(
          "client_attestation_trust_source is x5c but no trusted root is configured: client_id={}."
              + " Client authentication is refused rather than trusting the presented chain.",
          clientConfiguration.clientIdValue());
      return null;
    }

    if (!header.hasX5c()) {
      return null;
    }

    try {
      X509CertificateChain chain = X509CertificateChain.parse(header.x5c());
      chain.verifyToRoot(trustedRoots(clientConfiguration));

      JsonWebKey leafKey = JwkParser.parseFromCertificate(chain.leaf(), header.alg());
      return leafKey.toJwks();
    } catch (X509CertInvalidException e) {
      log.warn(
          "Client attestation x5c chain does not verify: client_id={}, reason={}",
          clientConfiguration.clientIdValue(),
          e.getMessage());
      return null;
    } catch (Exception e) {
      log.warn(
          "Failed to read the client attestation x5c chain: client_id={}, reason={}",
          clientConfiguration.clientIdValue(),
          e.getMessage());
      return null;
    }
  }

  private List<X509Certificate> trustedRoots(ClientConfiguration clientConfiguration)
      throws X509CertInvalidException {

    List<X509Certificate> roots = new ArrayList<>();
    for (String base64Der : clientConfiguration.clientAttestationTrustedRootCertificates()) {
      roots.add(X509CertificateChain.parseCertificate(base64Der));
    }
    return roots;
  }
}
