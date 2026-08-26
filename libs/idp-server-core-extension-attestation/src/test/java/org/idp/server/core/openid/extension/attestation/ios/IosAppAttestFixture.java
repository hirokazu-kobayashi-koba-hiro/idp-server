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

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import tools.jackson.dataformat.cbor.CBORMapper;

/**
 * Builds App Attest attestation objects, the way a device's Secure Enclave would.
 *
 * <p>What a device produces and what this builds differ in one way only: the chain leads to a root
 * generated here rather than to Apple's, which is why the client under test configures {@code
 * trusted_root_certificates}.
 *
 * @see <a
 *     href="https://developer.apple.com/documentation/devicecheck/validating-apps-that-connect-to-your-server">Validating
 *     apps that connect to your server</a>
 */
class IosAppAttestFixture {

  static final String APP_ID = "ABCDE12345.com.example.wallet";
  static final CBORMapper CBOR = CBORMapper.builder().build();

  /**
   * Stands in for the COSE encoded public key that follows credentialId.
   *
   * <p>The verifier reads nothing past credentialId — the key it needs is in the certificate — so
   * this only has to occupy the 77 bytes the layout allocates, and is deliberately not a real COSE
   * structure rather than a plausible looking one.
   */
  static final byte[] ENCODED_KEY_PLACEHOLDER = new byte[77];

  KeyPair rootKeyPair;
  X509Certificate rootCertificate;
  KeyPair intermediateKeyPair;
  X509Certificate intermediateCertificate;

  IosAppAttestFixture() throws Exception {
    this.rootKeyPair = generateKeyPair();
    this.rootCertificate =
        certificate(
            "CN=test-app-attest-root",
            rootKeyPair.getPublic(),
            "CN=test-app-attest-root",
            rootKeyPair,
            null);
    this.intermediateKeyPair = generateKeyPair();
    this.intermediateCertificate =
        certificate(
            "CN=test-app-attest-ca",
            intermediateKeyPair.getPublic(),
            "CN=test-app-attest-root",
            rootKeyPair,
            null);
  }

  static KeyPair generateKeyPair() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(new ECGenParameterSpec("secp256r1"));
    return generator.generateKeyPair();
  }

  String rootBase64() throws Exception {
    return Base64.getEncoder().encodeToString(rootCertificate.getEncoded());
  }

  Attestation attestation(KeyPair attestedKey) {
    return new Attestation(attestedKey);
  }

  /** The key identifier Apple derives: SHA-256 of the key in X9.62 uncompressed point format. */
  static byte[] keyIdentifier(ECPublicKey publicKey) throws Exception {
    return sha256(x962UncompressedPoint(publicKey));
  }

  /** An attestation object, with each element overridable so a single check can be made to fail. */
  class Attestation {

    KeyPair attestedKey;
    byte[] challenge = new byte[] {0x00};
    String appId = APP_ID;
    byte[] aaguid = IosAppAttestEnvironment.production.aaguid();
    long counter = 0;
    byte[] credentialId;
    String format = IosAppAttestObject.EXPECTED_FORMAT;
    boolean signedByUntrustedRoot = false;

    Attestation(KeyPair attestedKey) {
      this.attestedKey = attestedKey;
    }

    Attestation challenge(byte[] challenge) {
      this.challenge = challenge;
      return this;
    }

    Attestation appId(String appId) {
      this.appId = appId;
      return this;
    }

    Attestation environment(IosAppAttestEnvironment environment) {
      this.aaguid = environment.aaguid();
      return this;
    }

    Attestation counter(long counter) {
      this.counter = counter;
      return this;
    }

    Attestation credentialId(byte[] credentialId) {
      this.credentialId = credentialId;
      return this;
    }

    Attestation format(String format) {
      this.format = format;
      return this;
    }

    Attestation signedByUntrustedRoot() {
      this.signedByUntrustedRoot = true;
      return this;
    }

    /**
     * @return the attestation object, base64 encoded, as platform_evidence carries it
     */
    String build() throws Exception {
      byte[] authData = authenticatorData();
      byte[] nonce = sha256(concat(authData, sha256(challenge)));

      KeyPair issuer = signedByUntrustedRoot ? generateKeyPair() : intermediateKeyPair;
      String issuerName = signedByUntrustedRoot ? "CN=other-ca" : "CN=test-app-attest-ca";
      X509Certificate credentialCertificate =
          certificate("CN=attested-key", attestedKey.getPublic(), issuerName, issuer, nonce);

      List<byte[]> x5c = new ArrayList<>();
      x5c.add(credentialCertificate.getEncoded());
      x5c.add(
          signedByUntrustedRoot
              ? certificate("CN=other-ca", issuer.getPublic(), "CN=other-ca", issuer, null)
                  .getEncoded()
              : intermediateCertificate.getEncoded());

      Map<String, Object> attStmt = new LinkedHashMap<>();
      attStmt.put("x5c", x5c);
      attStmt.put("receipt", new byte[] {0x30, 0x00});

      Map<String, Object> object = new LinkedHashMap<>();
      object.put("fmt", format);
      object.put("attStmt", attStmt);
      object.put("authData", authData);

      return Base64.getEncoder().encodeToString(CBOR.writeValueAsBytes(object));
    }

    /**
     * rpIdHash ‖ flags ‖ counter ‖ aaguid ‖ credentialIdLength ‖ credentialId ‖ encoded key.
     *
     * <p>The flags byte sets AT, which is what a device sets when attested credential data follows.
     */
    private byte[] authenticatorData() throws Exception {
      byte[] id =
          credentialId != null
              ? credentialId
              : keyIdentifier((ECPublicKey) attestedKey.getPublic());

      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      stream.writeBytes(sha256(appId.getBytes(StandardCharsets.UTF_8)));
      stream.write(0x40);
      stream.writeBytes(ByteBuffer.allocate(4).putInt((int) counter).array());
      stream.writeBytes(aaguid);
      stream.writeBytes(ByteBuffer.allocate(2).putShort((short) id.length).array());
      stream.writeBytes(id);
      stream.writeBytes(ENCODED_KEY_PLACEHOLDER);
      return stream.toByteArray();
    }
  }

  /**
   * @param nonce when present, added as the App Attest nonce extension
   */
  private X509Certificate certificate(
      String subject,
      java.security.PublicKey publicKey,
      String issuer,
      KeyPair issuerKeyPair,
      byte[] nonce)
      throws Exception {

    Instant now = Instant.now();
    JcaX509v3CertificateBuilder builder =
        new JcaX509v3CertificateBuilder(
            new X500Principal(issuer),
            BigInteger.valueOf(System.nanoTime()),
            Date.from(now.minusSeconds(3600)),
            Date.from(now.plusSeconds(86400)),
            new X500Principal(subject),
            publicKey);

    if (nonce != null) {
      // SEQUENCE { [1] EXPLICIT OCTET STRING }, which is what Apple puts in 1.2.840.113635.100.8.2
      ASN1Encodable sequence =
          new DERSequence(new DERTaggedObject(true, 1, new DEROctetString(nonce)));
      builder.addExtension(
          new org.bouncycastle.asn1.ASN1ObjectIdentifier(IosAppAttestVerifier.NONCE_EXTENSION_OID),
          false,
          sequence);
    }

    ContentSigner signer =
        new JcaContentSignerBuilder("SHA256withECDSA").build(issuerKeyPair.getPrivate());
    return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
  }

  static byte[] sha256(byte[] input) throws Exception {
    return MessageDigest.getInstance("SHA-256").digest(input);
  }

  static byte[] concat(byte[] left, byte[] right) {
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    stream.writeBytes(left);
    stream.writeBytes(right);
    return stream.toByteArray();
  }

  static byte[] x962UncompressedPoint(ECPublicKey publicKey) {
    int fieldSize = (publicKey.getParams().getCurve().getField().getFieldSize() + 7) / 8;
    byte[] point = new byte[1 + fieldSize * 2];
    point[0] = 0x04;
    copyCoordinate(publicKey.getW().getAffineX(), point, 1, fieldSize);
    copyCoordinate(publicKey.getW().getAffineY(), point, 1 + fieldSize, fieldSize);
    return point;
  }

  private static void copyCoordinate(
      BigInteger coordinate, byte[] point, int offset, int fieldSize) {
    byte[] bytes = coordinate.toByteArray();
    int length = Math.min(bytes.length, fieldSize);
    System.arraycopy(bytes, bytes.length - length, point, offset + fieldSize - length, length);
  }
}
