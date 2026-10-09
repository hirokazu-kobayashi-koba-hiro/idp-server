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

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;

/**
 * Which CA a chain is verified up to is the verifier's choice (RFC 5280 Section 6: "The selection
 * of a trust anchor is a matter of policy"), and the anchor is not part of the path (Section 6.1).
 *
 * <p>Pinning the CA that issues the signing certificates, instead of a root that also issues for
 * other purposes, is what limits whose keys are accepted. These cases pin an intermediate, and
 * check that whether the presented chain carries the anchor — or certificates beyond it — does not
 * change the outcome (Issue #1957).
 */
class X509CertificateChainTrustAnchorTest {

  @Test
  void acceptsAChainEndingBelowAnIntermediateAnchor() throws Exception {
    Pki pki = Pki.create();

    X509CertificateChain chain = chainOf(pki.leaf);

    assertDoesNotThrow(() -> chain.verifyToTrustAnchor(List.of(pki.issuing.certificate)));
  }

  @Test
  void acceptsAChainCarryingTheIntermediateAnchor() throws Exception {
    Pki pki = Pki.create();

    X509CertificateChain chain = chainOf(pki.leaf, pki.issuing.certificate);

    assertDoesNotThrow(() -> chain.verifyToTrustAnchor(List.of(pki.issuing.certificate)));
  }

  @Test
  void acceptsAChainCarryingCertificatesBeyondTheAnchor() throws Exception {
    Pki pki = Pki.create();

    X509CertificateChain chain = chainOf(pki.leaf, pki.issuing.certificate, pki.root.certificate);

    assertDoesNotThrow(() -> chain.verifyToTrustAnchor(List.of(pki.issuing.certificate)));
  }

  @Test
  void rejectsALeafIssuedByAnotherCaUnderTheSameRoot() throws Exception {
    Pki pki = Pki.create();
    Authority otherIssuing = pki.root.issueCa("CN=Other Issuing CA", 0);
    X509Certificate otherLeaf = otherIssuing.issueEndEntity("CN=Other Service");

    X509CertificateChain chain = chainOf(otherLeaf, otherIssuing.certificate);

    assertThrows(
        X509CertInvalidException.class,
        () -> chain.verifyToTrustAnchor(List.of(pki.issuing.certificate)));
    // The same chain is accepted when the root is pinned, which is why pinning the root trusts
    // more than the attester's certificates.
    assertDoesNotThrow(() -> chain.verifyToTrustAnchor(List.of(pki.root.certificate)));
  }

  @Test
  void rejectsAChainHoldingOnlyTheAnchor() throws Exception {
    Pki pki = Pki.create();

    X509CertificateChain chain = chainOf(pki.issuing.certificate);

    X509CertInvalidException exception =
        assertThrows(
            X509CertInvalidException.class,
            () -> chain.verifyToTrustAnchor(List.of(pki.issuing.certificate)));
    assertTrue(
        exception.getMessage().contains("end entity"),
        "expected the leaf to be refused as a CA, but was: " + exception.getMessage());
  }

  @Test
  void rejectsASelfSignedLeaf() throws Exception {
    Authority selfSigned = Authority.selfSignedEndEntity("CN=Self Signed Leaf");

    X509CertificateChain chain = chainOf(selfSigned.certificate);

    assertThrows(
        X509CertInvalidException.class,
        () -> chain.verifyToTrustAnchor(List.of(selfSigned.certificate)));
  }

  @Test
  void acceptsAnAnchorReissuedWithTheSameKey() throws Exception {
    Pki pki = Pki.create();
    // Same subject and key, a new serial and validity: what renewing a CA certificate looks like.
    X509Certificate renewed = pki.root.reissue(pki.issuing, 0);

    X509CertificateChain chain = chainOf(pki.leaf, pki.issuing.certificate);

    assertDoesNotThrow(() -> chain.verifyToTrustAnchor(List.of(renewed)));
  }

  @Test
  void ignoresAnExpiredCertificateBeyondTheAnchor() throws Exception {
    Authority expiredRoot =
        Authority.root(
            "CN=Expired Root",
            1,
            Instant.now().minus(30, ChronoUnit.DAYS),
            Instant.now().minus(1, ChronoUnit.DAYS));
    Authority issuing = expiredRoot.issueCa("CN=Issuing CA", 0);
    X509Certificate leaf = issuing.issueEndEntity("CN=Attester Signer");

    X509CertificateChain chain = chainOf(leaf, issuing.certificate, expiredRoot.certificate);

    assertDoesNotThrow(() -> chain.verifyToTrustAnchor(List.of(issuing.certificate)));
  }

  @Test
  void rejectsAnExpiredAnchor() throws Exception {
    Authority expiredRoot =
        Authority.root(
            "CN=Expired Root",
            1,
            Instant.now().minus(30, ChronoUnit.DAYS),
            Instant.now().minus(1, ChronoUnit.DAYS));
    Authority issuing = expiredRoot.issueCa("CN=Issuing CA", 0);
    X509Certificate leaf = issuing.issueEndEntity("CN=Attester Signer");

    X509CertificateChain chain = chainOf(leaf, issuing.certificate);

    assertThrows(
        X509CertInvalidException.class,
        () -> chain.verifyToTrustAnchor(List.of(expiredRoot.certificate)));
  }

  @Test
  void doesNotCountTheAnchorAgainstItsOwnPathLenConstraint() throws Exception {
    // One CA below the root is what pathLenConstraint=1 allows. The root itself, carried in the
    // chain, is not part of the path and must not be counted as a second one.
    Pki pki = Pki.create();

    X509CertificateChain chain = chainOf(pki.leaf, pki.issuing.certificate, pki.root.certificate);

    assertDoesNotThrow(() -> chain.verifyToTrustAnchor(List.of(pki.root.certificate)));
  }

  @Test
  void rejectsAPathDeeperThanTheAnchorAllows() throws Exception {
    Authority root = Authority.root("CN=Strict Root", 0);
    Authority issuing = root.issueCa("CN=Issuing CA", 0);
    X509Certificate leaf = issuing.issueEndEntity("CN=Attester Signer");

    X509CertificateChain chain = chainOf(leaf, issuing.certificate);

    assertThrows(
        X509CertInvalidException.class, () -> chain.verifyToTrustAnchor(List.of(root.certificate)));
  }

  @Test
  void refusesToParseAChainLongerThanTheLimit() throws Exception {
    // Refused before any certificate is decoded, so the cost of an oversized chain stays bounded
    // for every entry point (verify and verifyToTrustAnchor alike).
    Pki pki = Pki.create();
    String[] encoded = new String[X509CertificateChain.MAX_CHAIN_LENGTH + 1];
    Arrays.fill(encoded, encode(pki.issuing.certificate));
    encoded[0] = encode(pki.leaf);

    X509CertInvalidException exception =
        assertThrows(
            X509CertInvalidException.class, () -> X509CertificateChain.parse(List.of(encoded)));
    assertTrue(exception.getMessage().contains("too long"), exception.getMessage());
  }

  @Test
  void parsesAChainAtTheLimit() throws Exception {
    Pki pki = Pki.create();
    String[] encoded = new String[X509CertificateChain.MAX_CHAIN_LENGTH];
    Arrays.fill(encoded, encode(pki.issuing.certificate));
    encoded[0] = encode(pki.leaf);

    assertDoesNotThrow(() -> X509CertificateChain.parse(List.of(encoded)));
  }

  // --- helpers ---

  /** Root CA → issuing CA → leaf, the shape Issue #1957 describes. */
  private record Pki(Authority root, Authority issuing, X509Certificate leaf) {

    static Pki create() throws Exception {
      Authority root = Authority.root("CN=Example Root CA", 1);
      Authority issuing = root.issueCa("CN=Example Attester Issuing CA", 0);
      X509Certificate leaf = issuing.issueEndEntity("CN=Attester Signer 001");
      return new Pki(root, issuing, leaf);
    }
  }

  private static X509CertificateChain chainOf(X509Certificate... certificates) throws Exception {
    return X509CertificateChain.parse(
        Arrays.stream(certificates).map(X509CertificateChainTrustAnchorTest::encode).toList());
  }

  private static String encode(X509Certificate certificate) {
    try {
      return Base64.getEncoder().encodeToString(certificate.getEncoded());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** A key plus the certificate that names it, able to issue certificates below itself. */
  private record Authority(KeyPair keyPair, X509Certificate certificate) {

    static Authority root(String subject, int pathLen) throws Exception {
      return root(
          subject,
          pathLen,
          Instant.now().minus(1, ChronoUnit.HOURS),
          Instant.now().plus(1, ChronoUnit.DAYS));
    }

    static Authority root(String subject, int pathLen, Instant notBefore, Instant notAfter)
        throws Exception {
      KeyPair keyPair = generateKeyPair();
      X509Certificate certificate =
          build(
              subject,
              keyPair.getPublic(),
              subject,
              keyPair.getPrivate(),
              pathLen,
              notBefore,
              notAfter);
      return new Authority(keyPair, certificate);
    }

    static Authority selfSignedEndEntity(String subject) throws Exception {
      KeyPair keyPair = generateKeyPair();
      X509Certificate certificate =
          build(
              subject,
              keyPair.getPublic(),
              subject,
              keyPair.getPrivate(),
              -1,
              Instant.now().minus(1, ChronoUnit.HOURS),
              Instant.now().plus(1, ChronoUnit.DAYS));
      return new Authority(keyPair, certificate);
    }

    Authority issueCa(String subject, int pathLen) throws Exception {
      KeyPair issuedKeyPair = generateKeyPair();
      return new Authority(issuedKeyPair, issue(subject, issuedKeyPair.getPublic(), pathLen));
    }

    X509Certificate issueEndEntity(String subject) throws Exception {
      return issue(subject, generateKeyPair().getPublic(), -1);
    }

    /** Issues {@code subordinate}'s certificate again, with its subject and key unchanged. */
    X509Certificate reissue(Authority subordinate, int pathLen) throws Exception {
      return issue(
          subordinate.certificate.getSubjectX500Principal().getName(),
          subordinate.keyPair.getPublic(),
          pathLen);
    }

    private X509Certificate issue(String subject, PublicKey subjectKey, int pathLen)
        throws Exception {
      return build(
          subject,
          subjectKey,
          certificate.getSubjectX500Principal().getName(),
          keyPair.getPrivate(),
          pathLen,
          Instant.now().minus(1, ChronoUnit.HOURS),
          Instant.now().plus(1, ChronoUnit.DAYS));
    }

    private static KeyPair generateKeyPair() throws Exception {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
      generator.initialize(256);
      return generator.generateKeyPair();
    }

    /**
     * @param pathLen -1 for an end entity (no BasicConstraints CA), otherwise the pathLenConstraint
     */
    private static X509Certificate build(
        String subject,
        PublicKey subjectKey,
        String issuer,
        PrivateKey issuerKey,
        int pathLen,
        Instant notBefore,
        Instant notAfter)
        throws Exception {

      JcaX509v3CertificateBuilder builder =
          new JcaX509v3CertificateBuilder(
              new X500Name(issuer),
              BigInteger.valueOf(System.nanoTime()),
              Date.from(notBefore),
              Date.from(notAfter),
              new X500Name(subject),
              subjectKey);

      if (pathLen >= 0) {
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(pathLen));
        builder.addExtension(
            Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
      } else {
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
      }

      return new JcaX509CertificateConverter()
          .getCertificate(
              builder.build(new JcaContentSignerBuilder("SHA256withECDSA").build(issuerKey)));
    }
  }
}
