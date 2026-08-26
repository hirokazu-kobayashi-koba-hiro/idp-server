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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.idp.server.core.openid.clientinstance.registration.PlatformAttestationVerificationException;
import org.idp.server.core.openid.clientinstance.registration.PlatformAttestationVerificationRequest;
import org.idp.server.core.openid.clientinstance.registration.PlatformAttestationVerifier;
import org.idp.server.platform.asn1.Asn1InvalidException;
import org.idp.server.platform.asn1.Asn1Node;
import org.idp.server.platform.jose.JsonWebKey;
import org.idp.server.platform.jose.JwkParser;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.x509.X509CertInvalidException;
import org.idp.server.platform.x509.X509CertificateChain;

/**
 * Verifies an Apple App Attest attestation presented at Client Instance registration.
 *
 * <p>The three bindings the {@link PlatformAttestationVerifier} contract requires are spread across
 * the object rather than sitting in one extension the way Android's are:
 *
 * <ol>
 *   <li><b>Challenge</b> — the certificate extension holds {@code nonce}, the hash of the
 *       authenticator data together with the hash of the challenge this registration was issued
 *   <li><b>Instance key</b> — the credential certificate certifies the key being registered, so its
 *       public key must equal {@code client_instance_public_key}
 *   <li><b>Application identity</b> — {@code rpIdHash} is the hash of the App ID, checked against
 *       the App IDs configured for this client
 * </ol>
 *
 * <p>On top of the bindings the chain is required to terminate at Apple's root, the counter must be
 * zero, and the environment must be the configured one. A chain that merely parses proves nothing:
 * without the root check an attacker would be writing the certificate extension themselves, and
 * every binding above would hold against values they chose.
 *
 * <p>Two of Apple's steps are not implemented, and both concern data rather than trust: {@code
 * apple_validation_category_01} and {@code apple_bundle_version_01} in the authenticator data
 * extensions. Requiring them would reject keys from iOS versions that predate them.
 *
 * @see <a
 *     href="https://developer.apple.com/documentation/devicecheck/validating-apps-that-connect-to-your-server">Validating
 *     apps that connect to your server</a>
 */
public class IosAppAttestVerifier implements PlatformAttestationVerifier {

  public static final String PLATFORM = "ios-app-attest";

  /** The credCert extension holding the nonce, as Apple specifies. */
  static final String NONCE_EXTENSION_OID = "1.2.840.113635.100.8.2";

  static final int NONCE_TAG_NUMBER = 1;

  LoggerWrapper log = LoggerWrapper.getLogger(IosAppAttestVerifier.class);
  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Override
  public String platform() {
    return PLATFORM;
  }

  @Override
  public void verify(PlatformAttestationVerificationRequest request) {
    IosAppAttestConfiguration configuration =
        IosAppAttestConfiguration.fromPlatformConfig(
            request.clientConfiguration().clientInstancePlatformConfig());

    IosAppAttestObject object = IosAppAttestObject.parse(request.evidence());
    throwExceptionIfFormatIsNotAppAttest(object);

    verifyChain(object.certificateChain(), configuration);

    X509Certificate credentialCertificate = object.certificateChain().leaf();
    IosAppAttestAuthenticatorData authenticatorData = object.authenticatorData();

    throwExceptionIfNonceDoesNotMatch(credentialCertificate, authenticatorData, request);
    throwExceptionIfInstanceKeyDoesNotMatch(credentialCertificate, request);
    throwExceptionIfCredentialIdDoesNotMatchKey(credentialCertificate, authenticatorData);
    throwExceptionIfApplicationDoesNotMatch(authenticatorData, configuration);
    throwExceptionIfCounterIsNotZero(authenticatorData);
    throwExceptionIfEnvironmentDoesNotMatch(authenticatorData, configuration);

    if (configuration.hasTrustedRootCertificates()) {
      log.warn(
          "App Attest verified against a configured root rather than the Apple App Attest root:"
              + " tenant={}, client_id={}. Hardware backing is only as trustworthy as that root.",
          request.tenant().identifierValue(),
          request.challenge().clientId());
    }
  }

  private void throwExceptionIfFormatIsNotAppAttest(IosAppAttestObject object) {
    if (!object.hasExpectedFormat()) {
      throw new IosAppAttestException(
          "attestation object fmt is not "
              + IosAppAttestObject.EXPECTED_FORMAT
              + ": "
              + object.format());
    }
  }

  /** Apple step 1: the chain terminates at a root held here rather than one it carries. */
  private void verifyChain(X509CertificateChain chain, IosAppAttestConfiguration configuration) {
    try {
      chain.verifyToRoot(trustedRoots(configuration));
    } catch (X509CertInvalidException e) {
      throw new IosAppAttestException("attestation chain does not verify: " + e.getMessage(), e);
    }
  }

  private List<X509Certificate> trustedRoots(IosAppAttestConfiguration configuration) {
    if (!configuration.hasTrustedRootCertificates()) {
      List<X509Certificate> shipped = AppleAttestationRoots.certificates();
      if (shipped.isEmpty()) {
        throw new IosAppAttestException(
            "no trusted attestation root is available; the shipped Apple root failed to load."
                + " Set client_instance_platform_config.ios_app_attest.trusted_root_certificates"
                + " to continue");
      }
      return shipped;
    }

    List<X509Certificate> configured = new ArrayList<>();
    for (String base64Der : configuration.trustedRootCertificates()) {
      try {
        configured.add(X509CertificateChain.parseCertificate(base64Der));
      } catch (X509CertInvalidException e) {
        throw new IosAppAttestException(
            "trusted_root_certificates contains a value that is not a certificate: "
                + e.getMessage(),
            e);
      }
    }
    return configured;
  }

  /**
   * Binding 1, Apple steps 2 to 4: the evidence was produced for this registration.
   *
   * <p>{@code nonce} is the hash of the authenticator data with the hash of the challenge appended,
   * and it sits inside a certificate Apple signed. That is what makes it unforgeable, and it is
   * also what binds the authenticator data to the certificate: neither can be swapped without
   * invalidating the other.
   *
   * <p>Apple specifies {@code clientDataHash} as "the SHA256 hash of the one-time challenge your
   * server sends", which leaves open what is hashed when the challenge travels as text. Here it is
   * the bytes the base64url challenge decodes to, never the characters, so that "the challenge" is
   * one thing across platforms — Android embeds those same bytes. An app that hashes the string it
   * received instead produces a nonce that will not match, and the failure is silent on its side,
   * so this is part of the client contract rather than an implementation detail.
   */
  private void throwExceptionIfNonceDoesNotMatch(
      X509Certificate credentialCertificate,
      IosAppAttestAuthenticatorData authenticatorData,
      PlatformAttestationVerificationRequest request) {

    byte[] challenge = Base64.getUrlDecoder().decode(request.challenge().challenge());
    byte[] expected = sha256(concat(authenticatorData.raw(), sha256(challenge)));

    if (!MessageDigest.isEqual(expected, nonceFrom(credentialCertificate))) {
      throw new IosAppAttestException(
          "the attestation nonce does not match the registration challenge");
    }
  }

  private byte[] nonceFrom(X509Certificate credentialCertificate) {
    byte[] extensionValue = credentialCertificate.getExtensionValue(NONCE_EXTENSION_OID);
    if (extensionValue == null) {
      throw new IosAppAttestException(
          "the credential certificate has no " + NONCE_EXTENSION_OID + " extension");
    }

    try {
      Asn1Node sequence = Asn1Node.parseExtension(extensionValue);
      return sequence
          .findTagged(NONCE_TAG_NUMBER)
          .orElseThrow(
              () ->
                  new IosAppAttestException(
                      "the " + NONCE_EXTENSION_OID + " extension has no [1] element"))
          .taggedContent()
          .octets();
    } catch (Asn1InvalidException e) {
      throw new IosAppAttestException(
          "failed to read the " + NONCE_EXTENSION_OID + " extension: " + e.getMessage(), e);
    }
  }

  /** Binding 2: the evidence covers the key being registered. */
  private void throwExceptionIfInstanceKeyDoesNotMatch(
      X509Certificate credentialCertificate, PlatformAttestationVerificationRequest request) {

    try {
      JsonWebKey instanceKey = JwkParser.parse(jsonConverter.write(request.instanceKey()));
      PublicKey registered = instanceKey.toPublicKey();

      if (!MessageDigest.isEqual(
          registered.getEncoded(), credentialCertificate.getPublicKey().getEncoded())) {
        throw new IosAppAttestException(
            "the credential certificate does not certify client_instance_public_key");
      }
    } catch (PlatformAttestationVerificationException e) {
      throw e;
    } catch (Exception e) {
      throw new IosAppAttestException(
          "failed to compare client_instance_public_key with the attested key: " + e.getMessage(),
          e);
    }
  }

  /**
   * Apple steps 5 and 9: the key identifier derived from the certificate equals {@code
   * credentialId}.
   *
   * <p>Apple has the server compare both against the key identifier the app sends. Here the app
   * sends the key itself, so the comparison is between the two halves of the evidence, which is
   * what those steps are for: it confirms the authenticator data describes the certified key.
   */
  private void throwExceptionIfCredentialIdDoesNotMatchKey(
      X509Certificate credentialCertificate, IosAppAttestAuthenticatorData authenticatorData) {

    byte[] keyIdentifier = sha256(x962UncompressedPoint(credentialCertificate.getPublicKey()));

    if (!MessageDigest.isEqual(keyIdentifier, authenticatorData.credentialId())) {
      throw new IosAppAttestException(
          "authData credentialId is not the key identifier of the certified key");
    }
  }

  /** Binding 3, Apple step 6: the attested application is this client's application. */
  private void throwExceptionIfApplicationDoesNotMatch(
      IosAppAttestAuthenticatorData authenticatorData, IosAppAttestConfiguration configuration) {

    for (String appId : configuration.appIds()) {
      byte[] expected = sha256(appId.getBytes(StandardCharsets.UTF_8));
      if (MessageDigest.isEqual(expected, authenticatorData.rpIdHash())) {
        return;
      }
    }

    throw new IosAppAttestException(
        "the attested App ID is not configured for this client: rpIdHash does not match any of "
            + configuration.appIds());
  }

  /** Apple step 7: a freshly attested key has never signed an assertion. */
  private void throwExceptionIfCounterIsNotZero(IosAppAttestAuthenticatorData authenticatorData) {
    if (authenticatorData.counter() != 0) {
      throw new IosAppAttestException(
          "authData counter is not 0 at attestation: " + authenticatorData.counter());
    }
  }

  /** Apple step 8: the key belongs to the environment this client is configured for. */
  private void throwExceptionIfEnvironmentDoesNotMatch(
      IosAppAttestAuthenticatorData authenticatorData, IosAppAttestConfiguration configuration) {

    if (!configuration.environment().matches(authenticatorData.aaguid())) {
      throw new IosAppAttestException(
          "the attested key was not generated in the configured environment: expected "
              + configuration.environment().name());
    }
  }

  /**
   * The public key in X9.62 uncompressed point format, which is what Apple hashes to form the key
   * identifier: {@code 0x04} followed by the affine coordinates, each padded to the field size.
   */
  private byte[] x962UncompressedPoint(PublicKey publicKey) {
    if (!(publicKey instanceof ECPublicKey ecPublicKey)) {
      throw new IosAppAttestException(
          "the credential certificate does not hold an EC key: " + publicKey.getAlgorithm());
    }

    int fieldSize = (ecPublicKey.getParams().getCurve().getField().getFieldSize() + 7) / 8;
    byte[] point = new byte[1 + fieldSize * 2];
    point[0] = 0x04;
    copyCoordinate(ecPublicKey.getW().getAffineX(), point, 1, fieldSize);
    copyCoordinate(ecPublicKey.getW().getAffineY(), point, 1 + fieldSize, fieldSize);
    return point;
  }

  /**
   * Writes a coordinate right aligned in {@code fieldSize} bytes.
   *
   * <p>{@link BigInteger#toByteArray()} is two's complement, so it carries a leading zero byte
   * whenever the top bit is set and is shorter than the field whenever the value is small. Both
   * have to be normalised, or the hash is taken over a different encoding than Apple's.
   */
  private void copyCoordinate(BigInteger coordinate, byte[] point, int offset, int fieldSize) {
    byte[] bytes = coordinate.toByteArray();
    int length = Math.min(bytes.length, fieldSize);
    System.arraycopy(bytes, bytes.length - length, point, offset + fieldSize - length, length);
  }

  private byte[] sha256(byte[] input) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(input);
    } catch (Exception e) {
      throw new IosAppAttestException("SHA-256 is not available: " + e.getMessage(), e);
    }
  }

  private byte[] concat(byte[] left, byte[] right) {
    ByteArrayOutputStream stream = new ByteArrayOutputStream(left.length + right.length);
    stream.writeBytes(left);
    stream.writeBytes(right);
    return stream.toByteArray();
  }
}
