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

import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.idp.server.core.openid.clientinstance.registration.PlatformAttestationEvidence;
import org.idp.server.core.openid.clientinstance.registration.PlatformAttestationVerificationException;
import org.idp.server.core.openid.clientinstance.registration.PlatformAttestationVerificationRequest;
import org.idp.server.core.openid.clientinstance.registration.PlatformAttestationVerifier;
import org.idp.server.platform.jose.JsonWebKey;
import org.idp.server.platform.jose.JwkParser;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.x509.X509CertificateChain;

/**
 * Verifies an Android key attestation presented at Client Instance registration.
 *
 * <p>The evidence is the certificate chain Android produces for a hardware-backed key:
 *
 * <pre>
 * "platform_evidence": {
 *   "platform": "android-key-attestation",
 *   "x5c": ["&lt;leaf DER base64&gt;", "&lt;intermediate&gt;", "&lt;root&gt;"]
 * }
 * </pre>
 *
 * <p>The three bindings the {@link PlatformAttestationVerifier} contract requires map onto the
 * chain directly, which is why Android is the platform where this is cleanest:
 *
 * <ol>
 *   <li><b>Challenge</b> — {@code attestationChallenge} in the leaf's key description equals the
 *       challenge this registration was issued
 *   <li><b>Instance key</b> — the leaf certifies the key being registered, so the leaf's public key
 *       must equal {@code client_instance_public_key}. The attestation is <i>of that key</i> rather
 *       than of a separate device key, so no additional hash construction is needed
 *   <li><b>Application identity</b> — {@code attestationApplicationId} names the package and the
 *       signing certificate digests, checked against the client's configuration
 * </ol>
 *
 * <p>On top of the bindings the chain is validated to a pinned root and the key itself is required
 * to be one secure hardware generated, holds, and will sign with. A chain that merely parses proves
 * nothing: without the root check any self-signed chain would satisfy every binding above, since
 * the attacker would be writing the extension themselves.
 *
 * <p>The properties of the key are checked separately from the bindings because they fail for a
 * different reason. A binding that does not hold means the evidence belongs to some other
 * registration; a key that was imported, or that KeyMint will not sign with, means the evidence is
 * genuinely about this registration and still does not support what registering it would claim.
 */
public class AndroidKeyAttestationVerifier implements PlatformAttestationVerifier {

  public static final String PLATFORM = "android-key-attestation";

  LoggerWrapper log = LoggerWrapper.getLogger(AndroidKeyAttestationVerifier.class);
  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
  AndroidCertificateChain chainReader = new AndroidCertificateChain();

  @Override
  public String platform() {
    return PLATFORM;
  }

  @Override
  public PlatformAttestationEvidence verify(PlatformAttestationVerificationRequest request) {
    AndroidKeyAttestationConfiguration configuration =
        AndroidKeyAttestationConfiguration.fromPlatformConfig(
            request.clientConfiguration().clientInstancePlatformConfig());

    X509CertificateChain chain = chainReader.read(request.evidence());
    chainReader.verifyChain(chain, configuration);

    X509Certificate leaf = chain.leaf();
    AndroidKeyAttestationExtension extension = AndroidKeyAttestationExtension.parse(leaf);

    throwExceptionIfChallengeDoesNotMatch(extension, request, configuration);
    throwExceptionIfInstanceKeyDoesNotMatch(leaf, request);
    throwExceptionIfApplicationDoesNotMatch(extension, configuration);
    throwExceptionIfSecurityLevelIsNotAccepted(extension, configuration);
    throwExceptionIfKeyWasNotGeneratedInSecureHardware(extension);
    throwExceptionIfKeyCannotSign(extension);
    throwExceptionIfBootIsNotAccepted(extension, configuration);
    throwExceptionIfOsPatchLevelIsTooOld(extension, configuration);

    if (configuration.hasTrustedRootCertificates()) {
      log.warn(
          "Android key attestation verified against a configured root rather than the Google hardware"
              + " attestation root: tenant={}, client_id={}. Hardware backing is only as trustworthy as"
              + " that root.",
          request.tenant().identifierValue(),
          request.challenge().clientId());
    }

    return evidenceOf(extension, chain);
  }

  /**
   * What the verification established, read from the fields the checks above accepted: the key's
   * security levels and origin from the hardware enforced list, and the application identity.
   */
  private PlatformAttestationEvidence evidenceOf(
      AndroidKeyAttestationExtension extension, X509CertificateChain chain) {
    return PlatformAttestationEvidence.of(
        PLATFORM,
        Map.of(
            "attestation_security_level", extension.attestationSecurityLevel().name(),
            "keymint_security_level", extension.keyMintSecurityLevel().name(),
            "origin", extension.origin().name()),
        Map.of(
            "package_names", extension.packageNames(),
            "signature_digests", extension.signatureDigests()),
        deviceOf(extension),
        chain);
  }

  /** The boot state and patch level the device reported, for re-evaluation when policy tightens. */
  private Map<String, Object> deviceOf(AndroidKeyAttestationExtension extension) {
    Map<String, Object> device = new LinkedHashMap<>();
    AndroidRootOfTrust rootOfTrust = extension.rootOfTrust();
    if (rootOfTrust.isReported()) {
      device.put("verified_boot_state", rootOfTrust.verifiedBootState().name());
      device.put("device_locked", rootOfTrust.isDeviceLocked());
    }
    if (extension.hasOsPatchLevel()) {
      device.put("os_patch_level", extension.osPatchLevel());
    }
    return device;
  }

  /**
   * Binding 1: the evidence was produced for this registration. What the app embeds is the client's
   * {@code challenge_binding}.
   */
  private void throwExceptionIfChallengeDoesNotMatch(
      AndroidKeyAttestationExtension extension,
      PlatformAttestationVerificationRequest request,
      AndroidKeyAttestationConfiguration configuration) {

    byte[] expected =
        configuration
            .challengeBinding()
            .challengeBytes(request.challenge().challenge(), request.instanceKey());

    if (!MessageDigest.isEqual(expected, extension.attestationChallenge())) {
      throw new PlatformAttestationVerificationException(
          "attestationChallenge does not match the registration challenge");
    }
  }

  /** Binding 2: the evidence covers the key being registered. */
  private void throwExceptionIfInstanceKeyDoesNotMatch(
      X509Certificate leaf, PlatformAttestationVerificationRequest request) {

    try {
      JsonWebKey instanceKey = JwkParser.parse(jsonConverter.write(request.instanceKey()));
      PublicKey registered = instanceKey.toPublicKey();

      if (!MessageDigest.isEqual(registered.getEncoded(), leaf.getPublicKey().getEncoded())) {
        throw new PlatformAttestationVerificationException(
            "the attested certificate does not certify client_instance_public_key");
      }
    } catch (PlatformAttestationVerificationException e) {
      throw e;
    } catch (Exception e) {
      throw new PlatformAttestationVerificationException(
          "failed to compare client_instance_public_key with the attested key: " + e.getMessage(),
          e);
    }
  }

  /** Binding 3: the attested application is this client's application. */
  private void throwExceptionIfApplicationDoesNotMatch(
      AndroidKeyAttestationExtension extension, AndroidKeyAttestationConfiguration configuration) {

    boolean packageMatches =
        extension.packageNames().stream().anyMatch(configuration.packageNames()::contains);
    if (!packageMatches) {
      throw new PlatformAttestationVerificationException(
          "attested package is not configured for this client: " + extension.packageNames());
    }

    // Every presented digest has to be configured. Accepting "any one matches" would let an
    // attacker append their own signing certificate to a genuine attestation.
    boolean digestsMatch =
        !extension.signatureDigests().isEmpty()
            && configuration.signatureDigests().containsAll(extension.signatureDigests());
    if (!digestsMatch) {
      throw new PlatformAttestationVerificationException(
          "attested signing certificate digests are not configured for this client");
    }
  }

  /**
   * Both security levels have to clear the configured minimum.
   *
   * <p>{@code attestationSecurityLevel} says where the attestation was produced and {@code
   * keyMintSecurityLevel} where the key lives. Checking only the first accepts a key held in
   * software whose attestation happens to have been signed in the TEE, and the hardware
   * AuthorizationList that {@code origin} and {@code purpose} are read from is exactly as
   * trustworthy as the second.
   */
  private void throwExceptionIfSecurityLevelIsNotAccepted(
      AndroidKeyAttestationExtension extension, AndroidKeyAttestationConfiguration configuration) {

    if (!configuration.accepts(extension.attestationSecurityLevel())) {
      throw new PlatformAttestationVerificationException(
          "attestation security level "
              + extension.attestationSecurityLevel().name()
              + " is below the configured minimum "
              + configuration.minSecurityLevel().name());
    }

    if (!configuration.accepts(extension.keyMintSecurityLevel())) {
      throw new PlatformAttestationVerificationException(
          "keyMint security level "
              + extension.keyMintSecurityLevel().name()
              + " is below the configured minimum "
              + configuration.minSecurityLevel().name());
    }
  }

  /**
   * The key was created by the secure hardware rather than handed to it.
   *
   * <p>An imported key satisfies every other check here — it lives in the TEE, it certifies the
   * registered public key, it names the right app — while a copy of the private key exists wherever
   * it was generated. Possession would then prove that <i>someone</i> holds it, not that this
   * device does, which is the claim registering a Client Instance makes.
   *
   * <p>A device that reports no {@code origin} is refused rather than trusted: the field is what
   * the check reads, and an absent one is not evidence of anything.
   */
  private void throwExceptionIfKeyWasNotGeneratedInSecureHardware(
      AndroidKeyAttestationExtension extension) {

    if (!extension.origin().isGeneratedInSecureHardware()) {
      throw new PlatformAttestationVerificationException(
          "the attested key was not generated in secure hardware: origin="
              + extension.origin().name());
    }
  }

  /**
   * The device booted an OS it verified, with the bootloader locked.
   *
   * <p>This is what the application identity above rests on. {@code attestationApplicationId} is
   * written by the Android platform, so a modified OS can name any application; the boot state is
   * written by KeyMint from what the bootloader measured. An OS that booted verified, on a device
   * whose bootloader cannot load another, is the one that named the application.
   *
   * <p>A device that reports no root of trust is refused, as with {@code origin}: an absent field
   * is not evidence that the device booted verified.
   */
  private void throwExceptionIfBootIsNotAccepted(
      AndroidKeyAttestationExtension extension, AndroidKeyAttestationConfiguration configuration) {

    AndroidRootOfTrust rootOfTrust = extension.rootOfTrust();
    if (!rootOfTrust.isReported()) {
      throw new PlatformAttestationVerificationException(
          "the attestation does not report the device's root of trust");
    }

    if (!configuration.verifiedBootStates().contains(rootOfTrust.verifiedBootState())) {
      throw new PlatformAttestationVerificationException(
          "verified boot state "
              + rootOfTrust.verifiedBootState().name()
              + " is not accepted: "
              + configuration.verifiedBootStates());
    }

    if (configuration.requiresDeviceLocked() && !rootOfTrust.isDeviceLocked()) {
      throw new PlatformAttestationVerificationException("the device's bootloader is unlocked");
    }
  }

  /**
   * The OS carries security patches at least as recent as configured.
   *
   * <p>No minimum by default. When one is set, a device that does not report its patch level is
   * refused, for the same reason as a missing root of trust.
   */
  private void throwExceptionIfOsPatchLevelIsTooOld(
      AndroidKeyAttestationExtension extension, AndroidKeyAttestationConfiguration configuration) {

    if (!configuration.hasMinOsPatchLevel()) {
      return;
    }
    if (!extension.hasOsPatchLevel()) {
      throw new PlatformAttestationVerificationException(
          "the attestation does not report the OS patch level");
    }
    if (extension.osPatchLevel() < configuration.minOsPatchLevel()) {
      throw new PlatformAttestationVerificationException(
          "OS patch level "
              + extension.osPatchLevel()
              + " is older than the configured minimum "
              + configuration.minOsPatchLevel());
    }
  }

  /** The key can produce the PoP signatures a Client Instance key exists to produce. */
  private void throwExceptionIfKeyCannotSign(AndroidKeyAttestationExtension extension) {
    if (!extension.purposes().contains(AndroidKeyPurpose.sign)) {
      throw new PlatformAttestationVerificationException(
          "the attested key is not authorized to sign: purpose=" + extension.purposes());
    }
  }
}
