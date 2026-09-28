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
import org.bouncycastle.asn1.ASN1Boolean;
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
 *
 * <p>{@code hardwareEnforced} is populated the way KeyMint fills it rather than left empty. A key
 * property the verifier reads from that list cannot be exercised against a fixture that does not
 * have one, and a fixture that omits what every real device sends is not a fixture of the real
 * thing — the same reason the root here carries CA constraints.
 */
class AndroidAttestationFixture {

  static final String PACKAGE_NAME = "com.example.wallet";

  /**
   * What an AuthorizationList says about the key, so a test can say "the same chain but imported".
   *
   * @param keyMintSecurityLevel where the key lives, which a device may report below the level it
   *     produced the attestation at
   * @param inSoftwareList writes {@code origin}, {@code purpose} and {@code rootOfTrust} into
   *     {@code softwareEnforced} instead, which is where a platform that wanted to claim them could
   *     put them
   * @param verifiedBootState {@code undefined} omits {@code rootOfTrust}
   * @param osPatchLevel YYYYMM; 0 omits {@code osPatchLevel}
   */
  record KeyProperties(
      AndroidKeyOrigin origin,
      List<AndroidKeyPurpose> purposes,
      AndroidKeyAttestationSecurityLevel keyMintSecurityLevel,
      boolean inSoftwareList,
      AndroidVerifiedBootState verifiedBootState,
      boolean deviceLocked,
      int osPatchLevel) {

    /**
     * What a device generating a signing key in secure hardware reports, having booted its stock OS
     * with the bootloader locked.
     */
    static KeyProperties ofDevice(AndroidKeyAttestationSecurityLevel securityLevel) {
      return new KeyProperties(
          AndroidKeyOrigin.generated,
          List.of(AndroidKeyPurpose.sign, AndroidKeyPurpose.verify),
          securityLevel,
          false,
          AndroidVerifiedBootState.verified,
          true,
          202409);
    }

    KeyProperties withOrigin(AndroidKeyOrigin replacement) {
      return new KeyProperties(
          replacement,
          purposes,
          keyMintSecurityLevel,
          inSoftwareList,
          verifiedBootState,
          deviceLocked,
          osPatchLevel);
    }

    KeyProperties withPurposes(List<AndroidKeyPurpose> replacement) {
      return new KeyProperties(
          origin,
          replacement,
          keyMintSecurityLevel,
          inSoftwareList,
          verifiedBootState,
          deviceLocked,
          osPatchLevel);
    }

    KeyProperties withKeyMintSecurityLevel(AndroidKeyAttestationSecurityLevel replacement) {
      return new KeyProperties(
          origin,
          purposes,
          replacement,
          inSoftwareList,
          verifiedBootState,
          deviceLocked,
          osPatchLevel);
    }

    KeyProperties withBoot(AndroidVerifiedBootState state, boolean locked) {
      return new KeyProperties(
          origin, purposes, keyMintSecurityLevel, inSoftwareList, state, locked, osPatchLevel);
    }

    KeyProperties withOsPatchLevel(int replacement) {
      return new KeyProperties(
          origin,
          purposes,
          keyMintSecurityLevel,
          inSoftwareList,
          verifiedBootState,
          deviceLocked,
          replacement);
    }

    /** The same values, moved to the list the platform writes. */
    KeyProperties movedToSoftwareList() {
      return new KeyProperties(
          origin,
          purposes,
          keyMintSecurityLevel,
          true,
          verifiedBootState,
          deviceLocked,
          osPatchLevel);
    }
  }

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
    return chain(
        attestedKey,
        challenge,
        securityLevel,
        packageName,
        signatureDigests,
        encodeSecurityLevelAsInteger,
        KeyProperties.ofDevice(securityLevel));
  }

  /** A chain whose hardware list says {@code properties} rather than what a device would say. */
  List<String> chain(
      KeyPair attestedKey,
      byte[] challenge,
      AndroidKeyAttestationSecurityLevel securityLevel,
      String packageName,
      List<byte[]> signatureDigests,
      KeyProperties properties)
      throws Exception {
    return chain(
        attestedKey, challenge, securityLevel, packageName, signatureDigests, false, properties);
  }

  private List<String> chain(
      KeyPair attestedKey,
      byte[] challenge,
      AndroidKeyAttestationSecurityLevel securityLevel,
      String packageName,
      List<byte[]> signatureDigests,
      boolean encodeSecurityLevelAsInteger,
      KeyProperties properties)
      throws Exception {

    X509Certificate leaf =
        leafCertificate(
            attestedKey,
            challenge,
            securityLevel,
            packageName,
            signatureDigests,
            encodeSecurityLevelAsInteger,
            properties);

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
      boolean encodeSecurityLevelAsInteger,
      KeyProperties properties)
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
            challenge,
            securityLevel,
            packageName,
            signatureDigests,
            encodeSecurityLevelAsInteger,
            properties));

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
      boolean encodeSecurityLevelAsInteger,
      KeyProperties properties)
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

    if (properties.inSoftwareList()) {
      addKeyProperties(softwareEnforced, properties);
    }

    // A device encodes SecurityLevel as ENUMERATED. Building it as INTEGER would make the tests
    // pass against an implementation that breaks on the first real chain.
    ASN1Encodable level =
        encodeSecurityLevelAsInteger
            ? new ASN1Integer(securityLevel.value)
            : new ASN1Enumerated(securityLevel.value);
    ASN1Encodable keyMintLevel =
        encodeSecurityLevelAsInteger
            ? new ASN1Integer(properties.keyMintSecurityLevel().value)
            : new ASN1Enumerated(properties.keyMintSecurityLevel().value);

    // KeyMint writes the key's own properties here, and only a value from this list is a statement
    // the secure hardware made.
    ASN1EncodableVector hardwareEnforced = new ASN1EncodableVector();
    if (!properties.inSoftwareList()) {
      addKeyProperties(hardwareEnforced, properties);
    }

    ASN1EncodableVector keyDescription = new ASN1EncodableVector();
    keyDescription.add(new ASN1Integer(4)); // attestationVersion
    keyDescription.add(level); // attestationSecurityLevel
    keyDescription.add(new ASN1Integer(4)); // keyMintVersion
    keyDescription.add(keyMintLevel); // keyMintSecurityLevel
    keyDescription.add(new DEROctetString(challenge)); // attestationChallenge
    keyDescription.add(new DEROctetString(new byte[0])); // uniqueId
    keyDescription.add(new DERSequence(softwareEnforced)); // softwareEnforced
    keyDescription.add(new DERSequence(hardwareEnforced)); // hardwareEnforced

    return new DERSequence(keyDescription);
  }

  /** An {@code undefined} origin or an empty purpose set stands for a device that omitted it. */
  private static void addKeyProperties(
      ASN1EncodableVector authorizationList, KeyProperties properties) {

    if (!properties.purposes().isEmpty()) {
      ASN1EncodableVector purposes = new ASN1EncodableVector();
      properties.purposes().forEach(purpose -> purposes.add(new ASN1Integer(purpose.value)));
      authorizationList.add(new DERTaggedObject(true, 1, new DERSet(purposes)));
    }
    if (properties.origin() != AndroidKeyOrigin.undefined) {
      authorizationList.add(
          new DERTaggedObject(true, 702, new ASN1Integer(properties.origin().value)));
    }
    if (properties.verifiedBootState() != AndroidVerifiedBootState.undefined) {
      ASN1EncodableVector rootOfTrust = new ASN1EncodableVector();
      rootOfTrust.add(new DEROctetString(new byte[32])); // verifiedBootKey
      rootOfTrust.add(ASN1Boolean.getInstance(properties.deviceLocked())); // deviceLocked
      rootOfTrust.add(new ASN1Enumerated(properties.verifiedBootState().value));
      rootOfTrust.add(new DEROctetString(new byte[32])); // verifiedBootHash
      authorizationList.add(new DERTaggedObject(true, 704, new DERSequence(rootOfTrust)));
    }
    if (properties.osPatchLevel() > 0) {
      authorizationList.add(
          new DERTaggedObject(true, 706, new ASN1Integer(properties.osPatchLevel())));
    }
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

    // Google's attestation root carries these; the verifier requires them of anything it treats as
    // an issuer, so a fixture without them is not a fixture of the real thing.
    builder.addExtension(
        org.bouncycastle.asn1.x509.Extension.basicConstraints,
        true,
        new org.bouncycastle.asn1.x509.BasicConstraints(true));
    builder.addExtension(
        org.bouncycastle.asn1.x509.Extension.keyUsage,
        true,
        new org.bouncycastle.asn1.x509.KeyUsage(
            org.bouncycastle.asn1.x509.KeyUsage.keyCertSign
                | org.bouncycastle.asn1.x509.KeyUsage.cRLSign));

    ContentSigner signer =
        new JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.getPrivate());
    return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
  }
}
