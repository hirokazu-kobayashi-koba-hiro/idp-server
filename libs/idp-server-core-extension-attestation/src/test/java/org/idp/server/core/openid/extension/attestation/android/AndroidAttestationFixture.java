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

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Enumerated;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Object;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * Builds an attestation chain the way a device would, so the verifier can be exercised without one.
 *
 * <p>The fixture is deliberately able to produce a <b>valid looking but untrusted</b> chain: that
 * is exactly what an attacker can do, and the tests rely on it to show that parsing alone decides
 * nothing.
 */
class AndroidAttestationFixture {

  static final String PACKAGE_NAME = "com.example.wallet";

  KeyPair rootKeyPair;
  X509Certificate rootCertificate;

  AndroidAttestationFixture() throws Exception {
    this.rootKeyPair = generateKeyPair();
    this.rootCertificate = selfSignedRoot(rootKeyPair);
  }

  static KeyPair generateKeyPair() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(new ECGenParameterSpec("secp256r1"));
    return generator.generateKeyPair();
  }

  String rootBase64() throws Exception {
    return Base64.getEncoder().encodeToString(rootCertificate.getEncoded());
  }

  /** A chain whose leaf certifies {@code attestedKey} and carries the key description extension. */
  List<String> chain(
      KeyPair attestedKey,
      byte[] challenge,
      AndroidKeyAttestationSecurityLevel securityLevel,
      String packageName,
      List<byte[]> signatureDigests)
      throws Exception {
    return chain(attestedKey, challenge, securityLevel, packageName, signatureDigests, false);
  }

  /**
   * @param encodeSecurityLevelAsInteger encodes SecurityLevel the way some encoders get it wrong
   */
  List<String> chain(
      KeyPair attestedKey,
      byte[] challenge,
      AndroidKeyAttestationSecurityLevel securityLevel,
      String packageName,
      List<byte[]> signatureDigests,
      boolean encodeSecurityLevelAsInteger)
      throws Exception {

    X509Certificate leaf =
        leafCertificate(
            attestedKey,
            challenge,
            securityLevel,
            packageName,
            signatureDigests,
            encodeSecurityLevelAsInteger);

    List<String> encoded = new ArrayList<>();
    encoded.add(Base64.getEncoder().encodeToString(leaf.getEncoded()));
    encoded.add(rootBase64());
    return encoded;
  }

  private X509Certificate leafCertificate(
      KeyPair attestedKey,
      byte[] challenge,
      AndroidKeyAttestationSecurityLevel securityLevel,
      String packageName,
      List<byte[]> signatureDigests,
      boolean encodeSecurityLevelAsInteger)
      throws Exception {

    Instant now = Instant.now();
    JcaX509v3CertificateBuilder builder =
        new JcaX509v3CertificateBuilder(
            new X500Principal("CN=attestation-root"),
            BigInteger.valueOf(now.toEpochMilli()),
            Date.from(now.minus(1, ChronoUnit.HOURS)),
            Date.from(now.plus(1, ChronoUnit.DAYS)),
            new X500Principal("CN=attested-key"),
            attestedKey.getPublic());

    builder.addExtension(
        new org.bouncycastle.asn1.ASN1ObjectIdentifier(AndroidKeyAttestationExtension.OID),
        false,
        keyDescription(
            challenge, securityLevel, packageName, signatureDigests, encodeSecurityLevelAsInteger));

    ContentSigner signer =
        new JcaContentSignerBuilder("SHA256withECDSA").build(rootKeyPair.getPrivate());
    return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
  }

  /** The KeyDescription SEQUENCE, with only the elements the verifier reads filled in. */
  private ASN1Object keyDescription(
      byte[] challenge,
      AndroidKeyAttestationSecurityLevel securityLevel,
      String packageName,
      List<byte[]> signatureDigests,
      boolean encodeSecurityLevelAsInteger)
      throws Exception {

    ASN1EncodableVector packageInfo = new ASN1EncodableVector();
    packageInfo.add(new DEROctetString(packageName.getBytes()));
    packageInfo.add(new ASN1Integer(1));

    ASN1EncodableVector digests = new ASN1EncodableVector();
    signatureDigests.forEach(digest -> digests.add(new DEROctetString(digest)));

    ASN1EncodableVector applicationId = new ASN1EncodableVector();
    applicationId.add(new DERSet(new DERSequence(packageInfo)));
    applicationId.add(new DERSet(digests));

    ASN1EncodableVector softwareEnforced = new ASN1EncodableVector();
    softwareEnforced.add(
        new DERTaggedObject(
            true, 709, new DEROctetString(new DERSequence(applicationId).getEncoded())));

    // A device encodes SecurityLevel as ENUMERATED. Building it as INTEGER would make the tests
    // pass against an implementation that breaks on the first real chain.
    ASN1Encodable level =
        encodeSecurityLevelAsInteger
            ? new ASN1Integer(securityLevel.value)
            : new ASN1Enumerated(securityLevel.value);

    ASN1EncodableVector keyDescription = new ASN1EncodableVector();
    keyDescription.add(new ASN1Integer(4)); // attestationVersion
    keyDescription.add(level); // attestationSecurityLevel
    keyDescription.add(new ASN1Integer(4)); // keyMintVersion
    keyDescription.add(level); // keyMintSecurityLevel
    keyDescription.add(new DEROctetString(challenge)); // attestationChallenge
    keyDescription.add(new DEROctetString(new byte[0])); // uniqueId
    keyDescription.add(new DERSequence(softwareEnforced)); // softwareEnforced
    keyDescription.add(new DERSequence()); // hardwareEnforced

    return new DERSequence(keyDescription);
  }

  private static X509Certificate selfSignedRoot(KeyPair keyPair) throws Exception {
    Instant now = Instant.now();
    JcaX509v3CertificateBuilder builder =
        new JcaX509v3CertificateBuilder(
            new X500Principal("CN=attestation-root"),
            BigInteger.ONE,
            Date.from(now.minus(1, ChronoUnit.HOURS)),
            Date.from(now.plus(365, ChronoUnit.DAYS)),
            new X500Principal("CN=attestation-root"),
            keyPair.getPublic());

    ContentSigner signer =
        new JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.getPrivate());
    return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
  }
}
