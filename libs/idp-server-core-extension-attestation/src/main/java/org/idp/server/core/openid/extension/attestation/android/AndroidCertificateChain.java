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

package org.idp.server.core.openid.extension.attestation.android;

import java.util.List;
import org.idp.server.core.openid.clientinstance.registration.PlatformAttestationVerificationException;
import org.idp.server.platform.x509.X509CertInvalidException;
import org.idp.server.platform.x509.X509CertificateChain;

/**
 * Reads the certificate chain of an Android key attestation and decides which roots it may lead to.
 *
 * <p>The chain checks themselves live in {@link X509CertificateChain}; what belongs here is the
 * Android-specific decision of <b>which</b> roots are trusted — the Google hardware attestation
 * root, or the roots a deployment configured in its place.
 */
class AndroidCertificateChain {

  /**
   * The roots shipped with this module, as base64url SHA-256 of their DER encodings.
   *
   * @see AndroidAttestationRoots
   */
  private static List<String> googleRoots() {
    return AndroidAttestationRoots.digests();
  }

  X509CertificateChain read(java.util.Map<String, Object> evidence) {
    Object x5c = evidence.get("x5c");
    if (!(x5c instanceof List<?> encodedList) || encodedList.isEmpty()) {
      throw new PlatformAttestationVerificationException(
          "platform_evidence.x5c must be a non-empty array of base64 encoded certificates");
    }

    try {
      return X509CertificateChain.parse(encodedList.stream().map(String::valueOf).toList());
    } catch (X509CertInvalidException e) {
      throw new PlatformAttestationVerificationException(
          "failed to parse platform_evidence.x5c: " + e.getMessage(), e);
    }
  }

  void verifyChain(X509CertificateChain chain, AndroidKeyAttestationConfiguration configuration) {
    try {
      chain.verify(trustedRoots(configuration));
    } catch (X509CertInvalidException e) {
      throw new PlatformAttestationVerificationException(
          "attestation chain does not verify: " + e.getMessage(), e);
    }
  }

  private List<String> trustedRoots(AndroidKeyAttestationConfiguration configuration) {
    if (!configuration.hasTrustedRootCertificates()) {
      List<String> shipped = googleRoots();
      if (shipped.isEmpty()) {
        throw new PlatformAttestationVerificationException(
            "no trusted attestation root is available; the shipped Google roots failed to load."
                + " Set client_instance_platform_config.android_key_attestation"
                + ".trusted_root_certificates to continue");
      }
      return shipped;
    }

    try {
      List<String> digests = configuration.trustedRootCertificates().stream().toList();
      return digests.stream()
          .map(
              base64Der -> {
                try {
                  return X509CertificateChain.sha256OfBase64Der(base64Der);
                } catch (X509CertInvalidException e) {
                  throw new PlatformAttestationVerificationException(
                      "trusted_root_certificates contains a value that is not a certificate: "
                          + e.getMessage(),
                      e);
                }
              })
          .toList();
    } catch (PlatformAttestationVerificationException e) {
      throw e;
    }
  }
}
