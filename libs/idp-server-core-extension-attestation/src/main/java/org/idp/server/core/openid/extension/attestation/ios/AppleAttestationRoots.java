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

package org.idp.server.core.openid.extension.attestation.ios;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.x509.X509CertInvalidException;
import org.idp.server.platform.x509.X509CertificateChain;

/**
 * The Apple App Attest root shipped with this module.
 *
 * <p>Unlike Android, the attestation object does not carry its root: Apple's {@code x5c} holds the
 * credential certificate and the intermediate only. The root is held here and the chain is required
 * to terminate at it, so it is kept as a certificate rather than as a digest.
 */
class AppleAttestationRoots {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(AppleAttestationRoots.class);

  private static final String RESOURCE_DIRECTORY = "ios-app-attest";

  /**
   * Files are listed rather than discovered: a classpath scan would pick up whatever a dependency
   * happens to ship under the same directory, and this list is a trust decision.
   */
  private static final List<String> RESOURCES = List.of("root-apple-app-attestation-ca.pem");

  private static final List<X509Certificate> CERTIFICATES = load();

  private AppleAttestationRoots() {}

  static List<X509Certificate> certificates() {
    return CERTIFICATES;
  }

  /** base64url SHA-256 digests of the shipped roots, for tests and for operational logging. */
  static List<String> digests() {
    List<String> digests = new ArrayList<>();
    for (X509Certificate certificate : CERTIFICATES) {
      try {
        digests.add(X509CertificateChain.sha256(certificate.getEncoded()));
      } catch (Exception e) {
        log.error("Failed to digest a loaded Apple attestation root: {}", e.getMessage());
      }
    }
    return List.copyOf(digests);
  }

  private static List<X509Certificate> load() {
    List<X509Certificate> certificates = new ArrayList<>();

    for (String resource : RESOURCES) {
      try {
        X509Certificate certificate = read(RESOURCE_DIRECTORY + "/" + resource);
        certificates.add(certificate);
        log.info(
            "Loaded Apple App Attest root: resource={}, subject={}, notAfter={}, sha256={}",
            resource,
            certificate.getSubjectX500Principal().getName(),
            certificate.getNotAfter(),
            X509CertificateChain.sha256(certificate.getEncoded()));
      } catch (Exception e) {
        log.error(
            "Failed to load Apple App Attest root: resource={}, reason={}."
                + " Registrations backed by this root will be rejected.",
            resource,
            e.getMessage());
      }
    }

    if (certificates.isEmpty()) {
      log.warn(
          "No Apple App Attest root was loaded. Every App Attest registration will be rejected"
              + " unless a client configures trusted_root_certificates.");
    }
    return List.copyOf(certificates);
  }

  private static X509Certificate read(String resource) throws Exception {
    try (InputStream stream =
        AppleAttestationRoots.class.getClassLoader().getResourceAsStream(resource)) {
      if (stream == null) {
        throw new X509CertInvalidException("resource not found: " + resource);
      }
      String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      byte[] der = Base64.getMimeDecoder().decode(pemBody(content));
      CertificateFactory factory = CertificateFactory.getInstance("X.509");
      return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(der));
    }
  }

  /** The base64 between the PEM markers, dropping the provenance comments above them. */
  private static String pemBody(String content) throws X509CertInvalidException {
    int begin = content.indexOf("-----BEGIN CERTIFICATE-----");
    int end = content.indexOf("-----END CERTIFICATE-----");
    if (begin < 0 || end < 0) {
      throw new X509CertInvalidException("resource does not contain a PEM certificate");
    }
    return content.substring(begin + "-----BEGIN CERTIFICATE-----".length(), end);
  }
}
