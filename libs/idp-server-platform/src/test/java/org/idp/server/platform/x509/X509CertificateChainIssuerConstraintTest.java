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
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * A chain is only evidence if the certificates above the leaf were allowed to issue it.
 *
 * <p>Checking that each certificate is signed by the next is not enough. Any signing key can sign a
 * certificate, so an end-entity key with a genuine chain can mint a leaf of its own and splice it
 * on top: every link verifies, the root is real, and the leaf — which is where an attestation
 * extension, a challenge and an instance key would be read from — is attacker controlled.
 */
class X509CertificateChainIssuerConstraintTest {

  @Test
  void acceptsAChainWhoseIssuersAreCas() throws Exception {
    Authority root = Authority.root("CN=Test Root", 1);
    Authority intermediate = root.issueCa("CN=Test Intermediate", 0);
    X509Certificate leaf = intermediate.issueEndEntity("CN=Test Leaf");

    X509CertificateChain chain = chainOf(leaf, intermediate.certificate, root.certificate);

    assertDoesNotThrow(() -> chain.verify(List.of(digestOf(root.certificate))));
  }

  @Test
  void rejectsALeafForgedByAnEndEntityKey() throws Exception {
    Authority root = Authority.root("CN=Test Root", 1);
    Authority intermediate = root.issueCa("CN=Test Intermediate", 0);

    // A genuine end-entity certificate, the kind an attacker can obtain for a key it controls.
    Authority genuineLeaf = intermediate.issueEndEntityAsAuthority("CN=Genuine Leaf");

    // Nothing stops that key from signing another certificate: the constraint lives in the
    // certificate, not in the key.
    X509Certificate forged = genuineLeaf.issueEndEntity("CN=Forged Leaf");

    X509CertificateChain chain =
        chainOf(forged, genuineLeaf.certificate, intermediate.certificate, root.certificate);

    X509CertInvalidException exception =
        assertThrows(
            X509CertInvalidException.class,
            () -> chain.verify(List.of(digestOf(root.certificate))));
    assertTrue(
        exception.getMessage().contains("is not a CA"),
        "expected the non-CA issuer to be named, but was: " + exception.getMessage());
  }

  @Test
  void rejectsAChainDeeperThanPathLenConstraintAllows() throws Exception {
    // pathLen 0 on the intermediate: it may issue end entities, but no further CA below it.
    Authority root = Authority.root("CN=Test Root", 1);
    Authority intermediate = root.issueCa("CN=Test Intermediate", 0);
    Authority extra = intermediate.issueCa("CN=Extra CA", 0);
    X509Certificate leaf = extra.issueEndEntity("CN=Test Leaf");

    X509CertificateChain chain =
        chainOf(leaf, extra.certificate, intermediate.certificate, root.certificate);

    X509CertInvalidException exception =
        assertThrows(
            X509CertInvalidException.class,
            () -> chain.verify(List.of(digestOf(root.certificate))));
    assertTrue(
        exception.getMessage().contains("pathLenConstraint"),
        "expected the path length to be named, but was: " + exception.getMessage());
  }

  @Test
  void rejectsAnIssuerThatDoesNotAllowKeyCertSign() throws Exception {
    Authority root = Authority.root("CN=Test Root", 1);
    // cA=TRUE but KeyUsage without keyCertSign: the certificate contradicts itself, and the
    // narrower statement wins.
    Authority intermediate = root.issueCaWithoutCertSign("CN=Test Intermediate");
    X509Certificate leaf = intermediate.issueEndEntity("CN=Test Leaf");

    X509CertificateChain chain = chainOf(leaf, intermediate.certificate, root.certificate);

    X509CertInvalidException exception =
        assertThrows(
            X509CertInvalidException.class,
            () -> chain.verify(List.of(digestOf(root.certificate))));
    assertTrue(
        exception.getMessage().contains("keyCertSign"),
        "expected keyCertSign to be named, but was: " + exception.getMessage());
  }

  @Test
  void verifyToRootRejectsALeafForgedByAnEndEntityKey() throws Exception {
    // Apple App Attest shape: the chain carries leaf and intermediate, the root is held by the
    // verifier.
    Authority root = Authority.root("CN=Test Root", 1);
    Authority intermediate = root.issueCa("CN=Test Intermediate", 0);
    Authority genuineLeaf = intermediate.issueEndEntityAsAuthority("CN=Genuine Leaf");
    X509Certificate forged = genuineLeaf.issueEndEntity("CN=Forged Leaf");

    X509CertificateChain chain = chainOf(forged, genuineLeaf.certificate, intermediate.certificate);

    assertThrows(
        X509CertInvalidException.class, () -> chain.verifyToRoot(List.of(root.certificate)));
  }

  @Test
  void verifyToRootAcceptsAGenuineChain() throws Exception {
    Authority root = Authority.root("CN=Test Root", 1);
    Authority intermediate = root.issueCa("CN=Test Intermediate", 0);
    X509Certificate leaf = intermediate.issueEndEntity("CN=Test Leaf");

    X509CertificateChain chain = chainOf(leaf, intermediate.certificate);

    assertDoesNotThrow(() -> chain.verifyToRoot(List.of(root.certificate)));
  }

  // --- helpers ---

  private static X509CertificateChain chainOf(X509Certificate... certificates) throws Exception {
    return X509CertificateChain.parse(
        java.util.Arrays.stream(certificates)
            .map(X509CertificateChainIssuerConstraintTest::encode)
            .toList());
  }

  private static String encode(X509Certificate certificate) {
    try {
      return Base64.getEncoder().encodeToString(certificate.getEncoded());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String digestOf(X509Certificate certificate) throws Exception {
    return X509CertificateChain.sha256(certificate.getEncoded());
  }

  /** A key plus the certificate that names it, able to issue certificates below itself. */
  private record Authority(KeyPair keyPair, X509Certificate certificate) {

    static Authority root(String subject, int pathLen) throws Exception {
      KeyPair keyPair = generateKeyPair();
      X509Certificate certificate =
          build(subject, keyPair.getPublic(), subject, keyPair.getPrivate(), pathLen, true);
      return new Authority(keyPair, certificate);
    }

    Authority issueCa(String subject, int pathLen) throws Exception {
      KeyPair keyPair = generateKeyPair();
      X509Certificate issued =
          build(
              subject,
              keyPair.getPublic(),
              certificate.getSubjectX500Principal().getName(),
              this.keyPair.getPrivate(),
              pathLen,
              true);
      return new Authority(keyPair, issued);
    }

    Authority issueCaWithoutCertSign(String subject) throws Exception {
      KeyPair keyPair = generateKeyPair();
      X509Certificate issued =
          build(
              subject,
              keyPair.getPublic(),
              certificate.getSubjectX500Principal().getName(),
              this.keyPair.getPrivate(),
              0,
              false);
      return new Authority(keyPair, issued);
    }

    X509Certificate issueEndEntity(String subject) throws Exception {
      return build(
          subject,
          generateKeyPair().getPublic(),
          certificate.getSubjectX500Principal().getName(),
          keyPair.getPrivate(),
          -1,
          false);
    }

    Authority issueEndEntityAsAuthority(String subject) throws Exception {
      KeyPair keyPair = generateKeyPair();
      X509Certificate issued =
          build(
              subject,
              keyPair.getPublic(),
              certificate.getSubjectX500Principal().getName(),
              this.keyPair.getPrivate(),
              -1,
              false);
      return new Authority(keyPair, issued);
    }

    private static KeyPair generateKeyPair() throws Exception {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
      generator.initialize(256);
      return generator.generateKeyPair();
    }

    /**
     * @param pathLen -1 for an end entity (no BasicConstraints CA), otherwise the pathLenConstraint
     * @param certSign whether to assert keyCertSign in KeyUsage
     */
    private static X509Certificate build(
        String subject,
        java.security.PublicKey subjectKey,
        String issuer,
        java.security.PrivateKey issuerKey,
        int pathLen,
        boolean certSign)
        throws Exception {

      Instant now = Instant.now();
      JcaX509v3CertificateBuilder builder =
          new JcaX509v3CertificateBuilder(
              new X500Name(issuer),
              BigInteger.valueOf(System.nanoTime()),
              Date.from(now.minus(1, ChronoUnit.HOURS)),
              Date.from(now.plus(1, ChronoUnit.DAYS)),
              new X500Name(subject),
              subjectKey);

      if (pathLen >= 0) {
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(pathLen));
        builder.addExtension(
            Extension.keyUsage,
            true,
            new KeyUsage(certSign ? KeyUsage.keyCertSign | KeyUsage.cRLSign : KeyUsage.cRLSign));
      } else {
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
      }

      return new JcaX509CertificateConverter()
          .getCertificate(
              builder.build(new JcaContentSignerBuilder("SHA256withECDSA").build(issuerKey)));
    }
  }
}
