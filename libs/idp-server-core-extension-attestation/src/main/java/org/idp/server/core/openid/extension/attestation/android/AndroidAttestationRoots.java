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
 * The Google hardware attestation roots shipped with this module.
 *
 * <p>Loaded from PEM resources rather than hard coded digests so the certificates themselves are
 * reviewable: a digest alone says nothing about what is being trusted. Each file records its
 * subject, validity and expected digest, and how to re-fetch it.
 *
 * <p>Loading is best effort per file. A resource that fails to parse is skipped with an error
 * rather than taking the process down — the remaining roots still verify the devices they cover,
 * and an empty list simply means every Android registration is rejected, which is the safe
 * direction.
 */
class AndroidAttestationRoots {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(AndroidAttestationRoots.class);

  private static final String RESOURCE_DIRECTORY = "android-key-attestation";

  /**
   * Files are listed rather than discovered: a classpath scan would pick up whatever a dependency
   * happens to ship under the same directory, and this list is a trust decision.
   */
  private static final List<String> RESOURCES =
      List.of("root-f92009e853b6b045.pem", "root-key-attestation-ca1.pem");

  private static final List<String> DIGESTS = load();

  private AndroidAttestationRoots() {}

  /** base64url SHA-256 digests of the shipped roots, in the form the chain check compares. */
  static List<String> digests() {
    return DIGESTS;
  }

  private static List<String> load() {
    List<String> digests = new ArrayList<>();

    for (String resource : RESOURCES) {
      try {
        X509Certificate certificate = read(RESOURCE_DIRECTORY + "/" + resource);
        String digest = X509CertificateChain.sha256(certificate.getEncoded());
        digests.add(digest);
        log.info(
            "Loaded Android attestation root: resource={}, subject={}, notAfter={}, sha256={}",
            resource,
            certificate.getSubjectX500Principal().getName(),
            certificate.getNotAfter(),
            digest);
      } catch (Exception e) {
        log.error(
            "Failed to load Android attestation root: resource={}, reason={}."
                + " Registrations backed by this root will be rejected.",
            resource,
            e.getMessage());
      }
    }

    if (digests.isEmpty()) {
      log.warn(
          "No Android attestation root was loaded. Every Android key attestation will be rejected"
              + " unless a client configures trusted_root_certificates.");
    }
    return List.copyOf(digests);
  }

  private static X509Certificate read(String resource) throws Exception {
    try (InputStream stream =
        AndroidAttestationRoots.class.getClassLoader().getResourceAsStream(resource)) {
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
