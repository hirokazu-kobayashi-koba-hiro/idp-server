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
import java.util.Base64;
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
 * <p>On top of the bindings the chain is validated to a pinned root and the key is required to live
 * in secure hardware. A chain that merely parses proves nothing: without the root check any
 * self-signed chain would satisfy every binding above, since the attacker would be writing the
 * extension themselves.
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
  public void verify(PlatformAttestationVerificationRequest request) {
    AndroidKeyAttestationConfiguration configuration =
        AndroidKeyAttestationConfiguration.fromPlatformConfig(
            request.clientConfiguration().clientInstancePlatformConfig());

    X509CertificateChain chain = chainReader.read(request.evidence());
    chainReader.verifyChain(chain, configuration);

    X509Certificate leaf = chain.leaf();
    AndroidKeyAttestationExtension extension = AndroidKeyAttestationExtension.parse(leaf);

    throwExceptionIfChallengeDoesNotMatch(extension, request);
    throwExceptionIfInstanceKeyDoesNotMatch(leaf, request);
    throwExceptionIfApplicationDoesNotMatch(extension, configuration);
    throwExceptionIfSecurityLevelIsNotAccepted(extension, configuration);

    if (configuration.hasTrustedRootCertificates()) {
      log.warn(
          "Android key attestation verified against a configured root rather than the Google hardware"
              + " attestation root: tenant={}, client_id={}. Hardware backing is only as trustworthy as"
              + " that root.",
          request.tenant().identifierValue(),
          request.challenge().clientId());
    }
  }

  /** Binding 1: the evidence was produced for this registration. */
  private void throwExceptionIfChallengeDoesNotMatch(
      AndroidKeyAttestationExtension extension, PlatformAttestationVerificationRequest request) {

    byte[] expected = Base64.getUrlDecoder().decode(request.challenge().challenge());

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

  private void throwExceptionIfSecurityLevelIsNotAccepted(
      AndroidKeyAttestationExtension extension, AndroidKeyAttestationConfiguration configuration) {

    if (!configuration.accepts(extension.attestationSecurityLevel())) {
      throw new PlatformAttestationVerificationException(
          "attestation security level "
              + extension.attestationSecurityLevel().name()
              + " is below the configured minimum "
              + configuration.minSecurityLevel().name());
    }
  }
}
