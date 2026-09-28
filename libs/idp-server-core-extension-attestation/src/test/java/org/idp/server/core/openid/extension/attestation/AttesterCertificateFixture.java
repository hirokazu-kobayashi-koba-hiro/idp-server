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

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * Builds the certificate chain a Client Attester would present in {@code x5c}.
 *
 * <p>One root issues any number of attester certificates, which is what lets a test rotate the
 * signing key under an unchanged root — the property the {@code x5c} trust source exists for.
 *
 * <p>The root carries CA constraints because the verifier requires them of anything it treats as an
 * issuer. A fixture without them would pass against an implementation that skips the check, and a
 * real chain would then fail.
 */
class AttesterCertificateFixture {

  KeyPair rootKeyPair;
  X509Certificate rootCertificate;

  AttesterCertificateFixture() throws Exception {
    ECKey rootKey = new ECKeyGenerator(Curve.P_256).generate();
    this.rootKeyPair = new KeyPair(rootKey.toPublicKey(), rootKey.toPrivateKey());
    this.rootCertificate = selfSignedRoot(rootKeyPair);
  }

  String rootBase64() throws Exception {
    return java.util.Base64.getEncoder().encodeToString(rootCertificate.getEncoded());
  }

  /**
   * An attester certificate issued by the root, with the key that signs the Client Attestation JWT.
   *
   * @param name distinguishes one attester certificate from the next, so a test can rotate
   */
  Attester issueAttester(String name) throws Exception {
    ECKey signingKey = new ECKeyGenerator(Curve.P_256).generate();
    KeyPair keyPair = new KeyPair(signingKey.toPublicKey(), signingKey.toPrivateKey());

    Instant now = Instant.now();
    JcaX509v3CertificateBuilder builder =
        new JcaX509v3CertificateBuilder(
            rootCertificate.getSubjectX500Principal(),
            BigInteger.valueOf(now.toEpochMilli()),
            Date.from(now.minus(1, ChronoUnit.HOURS)),
            Date.from(now.plus(1, ChronoUnit.DAYS)),
            new X500Principal("CN=" + name),
            keyPair.getPublic());

    ContentSigner signer =
        new JcaContentSignerBuilder("SHA256withECDSA").build(rootKeyPair.getPrivate());
    X509Certificate certificate =
        new JcaX509CertificateConverter().getCertificate(builder.build(signer));

    String leafBase64 = java.util.Base64.getEncoder().encodeToString(certificate.getEncoded());

    // Two shapes are produced because both occur. HAIP says the trust anchor MUST NOT be in the
    // chain, while an attester that simply serialises its whole chain will include it. The
    // verifier holds the root either way, so both have to work.
    return new Attester(
        signingKey, certificate, List.of(leafBase64, rootBase64()), List.of(leafBase64));
  }

  private static X509Certificate selfSignedRoot(KeyPair keyPair) throws Exception {
    Instant now = Instant.now();
    JcaX509v3CertificateBuilder builder =
        new JcaX509v3CertificateBuilder(
            new X500Principal("CN=attester-root"),
            BigInteger.ONE,
            Date.from(now.minus(1, ChronoUnit.HOURS)),
            Date.from(now.plus(365, ChronoUnit.DAYS)),
            new X500Principal("CN=attester-root"),
            keyPair.getPublic());

    builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
    builder.addExtension(
        Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));

    ContentSigner signer =
        new JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.getPrivate());
    return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
  }

  /**
   * @param x5c the chain including the root, as a serialised chain carries it
   * @param x5cWithoutRoot the chain excluding the trust anchor, the shape HAIP requires
   */
  record Attester(
      ECKey signingKey,
      X509Certificate certificate,
      List<String> x5c,
      List<String> x5cWithoutRoot) {}
}
