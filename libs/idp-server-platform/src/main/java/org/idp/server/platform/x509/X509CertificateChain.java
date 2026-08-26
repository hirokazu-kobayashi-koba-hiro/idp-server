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

package org.idp.server.platform.x509;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * A certificate chain presented by a caller, and the checks that turn it into evidence.
 *
 * <p>A chain that parses proves nothing: anyone can generate one whose contents say whatever they
 * like. It becomes evidence only when it leads to a root the verifier decided to trust ahead of
 * time, which is why {@link #verify} takes the trusted roots rather than reading them from the
 * chain.
 */
public class X509CertificateChain {

  List<X509Certificate> certificates;

  X509CertificateChain(List<X509Certificate> certificates) {
    this.certificates = certificates;
  }

  /**
   * Parses base64 encoded DER certificates, leaf first.
   *
   * @throws X509CertInvalidException when any element is not a certificate
   */
  public static X509CertificateChain parse(List<String> base64DerList)
      throws X509CertInvalidException {
    if (base64DerList == null || base64DerList.isEmpty()) {
      throw new X509CertInvalidException("certificate chain is empty");
    }

    try {
      CertificateFactory factory = CertificateFactory.getInstance("X.509");
      List<X509Certificate> certificates = new ArrayList<>();
      for (String base64Der : base64DerList) {
        byte[] der = Base64.getDecoder().decode(base64Der.replaceAll("\\s", ""));
        certificates.add(
            (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(der)));
      }
      return new X509CertificateChain(certificates);
    } catch (Exception e) {
      throw new X509CertInvalidException(e);
    }
  }

  /**
   * Verifies that every certificate is inside its validity window, that each is signed by the next,
   * and that the last one is one of {@code trustedRootSha256}.
   *
   * @param trustedRootSha256 base64url encoded SHA-256 digests of the DER encoded trusted roots.
   *     Digests rather than certificates, so a root reissued with the same key is not accepted
   *     silently.
   * @throws X509CertInvalidException when any check fails
   */
  public void verify(List<String> trustedRootSha256) throws X509CertInvalidException {
    if (certificates.size() < 2) {
      throw new X509CertInvalidException("certificate chain must contain a leaf and a root");
    }
    if (trustedRootSha256 == null || trustedRootSha256.isEmpty()) {
      throw new X509CertInvalidException("no trusted root is configured");
    }

    try {
      for (X509Certificate certificate : certificates) {
        certificate.checkValidity();
      }
      for (int i = 0; i < certificates.size() - 1; i++) {
        certificates.get(i).verify(certificates.get(i + 1).getPublicKey());
      }
      X509Certificate root = certificates.get(certificates.size() - 1);
      root.verify(root.getPublicKey());
    } catch (Exception e) {
      throw new X509CertInvalidException(e);
    }

    if (!trustedRootSha256.contains(rootSha256())) {
      throw new X509CertInvalidException("certificate chain does not lead to a trusted root");
    }
  }

  /**
   * Verifies that every certificate is inside its validity window, that each is signed by the next,
   * and that the last one is signed by one of {@code trustedRoots}.
   *
   * <p>For chains that do not carry their own root. Apple App Attest is the case this exists for:
   * the evidence holds the leaf and the intermediate only, and the root is one the verifier already
   * holds, so there is nothing in the chain to pin a digest against. Passing the root as a
   * certificate rather than a digest is what makes the terminating check a signature check.
   *
   * @throws X509CertInvalidException when any check fails
   */
  public void verifyToRoot(List<X509Certificate> trustedRoots) throws X509CertInvalidException {
    if (certificates.isEmpty()) {
      throw new X509CertInvalidException("certificate chain is empty");
    }
    if (trustedRoots == null || trustedRoots.isEmpty()) {
      throw new X509CertInvalidException("no trusted root is configured");
    }

    try {
      for (X509Certificate certificate : certificates) {
        certificate.checkValidity();
      }
      for (int i = 0; i < certificates.size() - 1; i++) {
        certificates.get(i).verify(certificates.get(i + 1).getPublicKey());
      }
    } catch (Exception e) {
      throw new X509CertInvalidException(e);
    }

    X509Certificate last = certificates.get(certificates.size() - 1);
    for (X509Certificate trustedRoot : trustedRoots) {
      try {
        trustedRoot.checkValidity();
        last.verify(trustedRoot.getPublicKey());
        return;
      } catch (Exception e) {
        // Try the next root: several may be configured, and only one has to sign this chain.
      }
    }

    throw new X509CertInvalidException("certificate chain does not lead to a trusted root");
  }

  /** Parses a single base64 encoded DER certificate. */
  public static X509Certificate parseCertificate(String base64Der) throws X509CertInvalidException {
    try {
      byte[] der = Base64.getDecoder().decode(base64Der.replaceAll("\\s", ""));
      CertificateFactory factory = CertificateFactory.getInstance("X.509");
      return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(der));
    } catch (Exception e) {
      throw new X509CertInvalidException(e);
    }
  }

  /** base64url encoded SHA-256 of a DER encoded certificate, the form {@link #verify} compares. */
  public static String sha256(byte[] der) throws X509CertInvalidException {
    try {
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(MessageDigest.getInstance("SHA-256").digest(der));
    } catch (Exception e) {
      throw new X509CertInvalidException(e);
    }
  }

  public static String sha256OfBase64Der(String base64Der) throws X509CertInvalidException {
    return sha256(Base64.getDecoder().decode(base64Der.replaceAll("\\s", "")));
  }

  public X509Certificate leaf() {
    return certificates.get(0);
  }

  public X509Certificate root() {
    return certificates.get(certificates.size() - 1);
  }

  public String rootSha256() throws X509CertInvalidException {
    try {
      return sha256(root().getEncoded());
    } catch (X509CertInvalidException e) {
      throw e;
    } catch (Exception e) {
      throw new X509CertInvalidException(e);
    }
  }

  public int size() {
    return certificates.size();
  }
}
