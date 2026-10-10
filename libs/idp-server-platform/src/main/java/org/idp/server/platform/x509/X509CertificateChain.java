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
import java.security.cert.CertPathBuilder;
import java.security.cert.CertStore;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXCertPathBuilderResult;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A certificate chain presented by a caller, and the checks that turn it into evidence.
 *
 * <p>A chain that parses proves nothing: anyone can generate one whose contents say whatever they
 * like. It becomes evidence only when it leads to a root the verifier decided to trust ahead of
 * time, which is why {@link #verify} takes the trusted roots rather than reading them from the
 * chain.
 */
public class X509CertificateChain {

  /**
   * Longest chain accepted.
   *
   * <p>A chain arrives before its sender is authenticated, so its length is the sender's choice.
   * Every certificate costs a decode, and in path building a signature check per candidate issuer.
   * The limit is applied before anything is decoded, which bounds both for every entry point. Real
   * chains are a few certificates long (Apple App Attest 2, Android Key Attestation up to about 5);
   * 10 is the limit Keycloak applies to x5c as well ({@code MAX_CERTIFICATE_CHAIN_LENGTH} in <a
   * href="https://github.com/keycloak/keycloak/blob/main/core/src/main/java/org/keycloak/crypto/X509CertificateChainValidator.java">X509CertificateChainValidator</a>).
   */
  static final int MAX_CHAIN_LENGTH = 10;

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
    if (base64DerList.size() > MAX_CHAIN_LENGTH) {
      throw new X509CertInvalidException("certificate chain is too long");
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
        X509Certificate issuer = certificates.get(i + 1);
        // The issuer sits at index i+1, so exactly i CA certificates stand between it and the
        // leaf — which is what pathLenConstraint bounds.
        verifyIssuerIsCa(issuer, i);
        certificates.get(i).verify(issuer.getPublicKey());
      }
      X509Certificate root = certificates.get(certificates.size() - 1);
      root.verify(root.getPublicKey());
    } catch (X509CertInvalidException e) {
      throw e;
    } catch (Exception e) {
      throw new X509CertInvalidException(e);
    }

    if (!trustedRootSha256.contains(rootSha256())) {
      throw new X509CertInvalidException("certificate chain does not lead to a trusted root");
    }
  }

  /**
   * Verifies that the chain leads from its leaf to one of {@code trustAnchors}, by RFC 5280 path
   * validation.
   *
   * <p>A trust anchor is a name and a public key (RFC 5280 Section 6.1.1 (d)): when it is given as
   * a certificate, its subject and subjectPublicKeyInfo are what is used. So the anchor can be a
   * root or any CA below one — pinning the CA that issues the signing certificates, rather than a
   * root that also issues for other purposes, is what narrows whose keys are accepted.
   *
   * <p>The chain is treated as the material to build the path from, not as the path itself. The
   * path starts at the certificate the anchor issued and ends at the leaf, and the anchor is not
   * part of it (RFC 5280 Section 6.1). So the presented chain may or may not carry the anchor, and
   * anything beyond the anchor — a root above a pinned intermediate, for instance — is not read,
   * neither for its signature nor for its validity. Apple App Attest is the case without the root
   * (the evidence holds the leaf and the intermediate only); an attester serialising its whole
   * chain is the case with it.
   *
   * <p>The chain's length is already bounded by {@link #parse}. The path itself is built and
   * validated by the platform's PKIX implementation, which applies basicConstraints,
   * pathLenConstraint and keyUsage to each issuer in it. On top of that:
   *
   * <ul>
   *   <li>The leaf has to be an end entity, not self-signed. Otherwise a chain holding only the
   *       anchor would make an empty path, and the anchor's own key would be accepted as a signing
   *       key. A self-signed end entity could only get through by being configured as an anchor
   *       itself, which the anchor check below refuses as well (an empty path counts -1 CAs, and an
   *       end entity is not a CA); the leaf check states the rule where the leaf is read.
   *   <li>The anchor has to be within its validity window and allowed to issue, counting the CAs
   *       between it and the leaf in the built path. RFC 5280 does not require either for an
   *       anchor; they are kept so that an expired or non-CA certificate configured by mistake is
   *       not trusted.
   *   <li>Revocation is not checked; no revocation source is configured.
   * </ul>
   *
   * <p>The PKIX implementation also applies {@code jdk.certpath.disabledAlgorithms} to every
   * certificate in the path, so chains signed with algorithms or key sizes the JDK has disabled are
   * refused.
   *
   * @throws X509CertInvalidException when any check fails
   */
  public void verifyToTrustAnchor(List<X509Certificate> trustAnchors)
      throws X509CertInvalidException {
    verifyToTrustAnchor(trustAnchors, new Date());
  }

  /**
   * {@link #verifyToTrustAnchor(List)} at a given time, for evidence whose certificates are checked
   * against a fixed moment (a recorded attestation replayed in a test, for instance).
   *
   * @param validationDate the time every validity window, the anchor's included, is checked at
   * @throws X509CertInvalidException when any check fails
   */
  public void verifyToTrustAnchor(List<X509Certificate> trustAnchors, Date validationDate)
      throws X509CertInvalidException {
    if (certificates.isEmpty()) {
      throw new X509CertInvalidException("certificate chain is empty");
    }
    if (trustAnchors == null || trustAnchors.isEmpty()) {
      throw new X509CertInvalidException("no trusted root is configured");
    }

    X509Certificate leaf = leaf();
    verifyLeafIsEndEntity(leaf);

    PKIXCertPathBuilderResult result = buildPath(leaf, trustAnchors, validationDate);

    X509Certificate anchor = result.getTrustAnchor().getTrustedCert();
    int subordinateCaCount = result.getCertPath().getCertificates().size() - 1;
    try {
      anchor.checkValidity(validationDate);
    } catch (Exception e) {
      throw new X509CertInvalidException(e);
    }
    verifyIssuerIsCa(anchor, subordinateCaCount);
  }

  private PKIXCertPathBuilderResult buildPath(
      X509Certificate leaf, List<X509Certificate> trustAnchors, Date validationDate)
      throws X509CertInvalidException {
    try {
      Set<TrustAnchor> anchors = new HashSet<>();
      for (X509Certificate trustAnchor : trustAnchors) {
        anchors.add(new TrustAnchor(trustAnchor, null));
      }

      X509CertSelector target = new X509CertSelector();
      target.setCertificate(leaf);

      PKIXBuilderParameters parameters = new PKIXBuilderParameters(anchors, target);
      parameters.addCertStore(
          CertStore.getInstance("Collection", new CollectionCertStoreParameters(certificates)));
      parameters.setRevocationEnabled(false);
      parameters.setDate(validationDate);

      return (PKIXCertPathBuilderResult) CertPathBuilder.getInstance("PKIX").build(parameters);
    } catch (Exception e) {
      throw new X509CertInvalidException(
          "certificate chain does not lead to a trusted root: " + e.getMessage(), e);
    }
  }

  private void verifyLeafIsEndEntity(X509Certificate leaf) throws X509CertInvalidException {
    if (leaf.getBasicConstraints() >= 0) {
      throw new X509CertInvalidException(
          "certificate chain leaf must be an end entity certificate: "
              + leaf.getSubjectX500Principal());
    }
    if (!leaf.getSubjectX500Principal().equals(leaf.getIssuerX500Principal())) {
      return;
    }
    try {
      leaf.verify(leaf.getPublicKey());
    } catch (Exception e) {
      // Same subject and issuer names but not self-signed: an ordinary leaf.
      return;
    }
    throw new X509CertInvalidException("certificate chain leaf must not be self-signed");
  }

  /**
   * Requires that a certificate used as an issuer is actually allowed to issue.
   *
   * <p>Link signature checks alone do not establish a chain. Any signing key can sign a
   * certificate, including an end-entity key: on Android, an app can generate a {@code
   * PURPOSE_SIGN} key in the Keystore, obtain a genuine attestation chain for it, and then use that
   * key to sign a leaf of its own choosing. Splicing that forged leaf on top of the genuine chain
   * produces a chain where every link verifies and the root is the real one, while the leaf — which
   * is where the attestation extension, the challenge and the instance key are read from — is
   * entirely attacker controlled.
   *
   * <p>What stops it is the constraint the issuer carries about itself:
   *
   * <ul>
   *   <li>{@code BasicConstraints cA=TRUE}, the statement that this certificate may sign others
   *   <li>{@code pathLenConstraint}, the number of CAs allowed below it
   *   <li>{@code KeyUsage keyCertSign}, when the extension is present at all
   * </ul>
   *
   * @param issuer the certificate whose key signs the one below it
   * @param subordinateCaCount how many CA certificates sit between {@code issuer} and the leaf,
   *     which is what {@code pathLenConstraint} bounds
   */
  private void verifyIssuerIsCa(X509Certificate issuer, int subordinateCaCount)
      throws X509CertInvalidException {

    // getBasicConstraints() returns the pathLenConstraint for a CA (Integer.MAX_VALUE when absent)
    // and -1 when the certificate is not a CA at all.
    int pathLenConstraint = issuer.getBasicConstraints();
    if (pathLenConstraint < 0) {
      throw new X509CertInvalidException(
          "certificate used as an issuer is not a CA: " + issuer.getSubjectX500Principal());
    }
    if (pathLenConstraint < subordinateCaCount) {
      throw new X509CertInvalidException(
          "certificate chain exceeds pathLenConstraint of "
              + issuer.getSubjectX500Principal()
              + ": allows "
              + pathLenConstraint
              + ", chain has "
              + subordinateCaCount);
    }

    boolean[] keyUsage = issuer.getKeyUsage();
    // Index 5 is keyCertSign (RFC 5280 4.2.1.3). A certificate without the extension is not
    // constrained by it.
    if (keyUsage != null && keyUsage.length > 5 && !keyUsage[5]) {
      throw new X509CertInvalidException(
          "certificate used as an issuer does not allow keyCertSign: "
              + issuer.getSubjectX500Principal());
    }
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

  /** The certificates as presented, leaf first. */
  public List<X509Certificate> certificates() {
    return List.copyOf(certificates);
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
