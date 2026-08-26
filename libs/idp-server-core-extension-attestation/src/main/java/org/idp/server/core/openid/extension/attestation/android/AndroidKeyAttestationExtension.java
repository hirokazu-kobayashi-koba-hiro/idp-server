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

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.idp.server.platform.asn1.Asn1InvalidException;
import org.idp.server.platform.asn1.Asn1Node;

/**
 * The Android key attestation extension of a leaf certificate (OID {@code
 * 1.3.6.1.4.1.11129.2.1.17}).
 *
 * <p>The extension is what makes the certificate an attestation rather than an ordinary
 * certificate: it states the challenge the key was created for, which application asked for it, and
 * where the key lives. Everything this verifier decides comes from here, so the parse is strict —
 * an element that is absent or shaped differently than the schema is an error rather than a
 * default.
 *
 * <pre>
 * KeyDescription ::= SEQUENCE {
 *   attestationVersion         INTEGER,
 *   attestationSecurityLevel   SecurityLevel,     -- ENUMERATED, not INTEGER
 *   keyMintVersion             INTEGER,
 *   keyMintSecurityLevel       SecurityLevel,     -- ENUMERATED, not INTEGER
 *   attestationChallenge       OCTET_STRING,
 *   uniqueId                   OCTET_STRING,
 *   softwareEnforced           AuthorizationList,
 *   hardwareEnforced           AuthorizationList,
 * }
 *
 * SecurityLevel ::= ENUMERATED { Software (0), TrustedEnvironment (1), StrongBox (2) }
 * </pre>
 *
 * @see <a href="https://developer.android.com/privacy-and-security/security-key-attestation">Key
 *     attestation</a>
 */
public class AndroidKeyAttestationExtension {

  static final String OID = "1.3.6.1.4.1.11129.2.1.17";

  /** Tag of {@code attestationApplicationId} inside an AuthorizationList. */
  private static final int ATTESTATION_APPLICATION_ID_TAG = 709;

  private static final int ATTESTATION_SECURITY_LEVEL_INDEX = 1;
  private static final int ATTESTATION_CHALLENGE_INDEX = 4;
  private static final int SOFTWARE_ENFORCED_INDEX = 6;
  private static final int HARDWARE_ENFORCED_INDEX = 7;
  private static final int KEY_DESCRIPTION_ELEMENTS = 8;

  byte[] attestationChallenge;
  AndroidKeyAttestationSecurityLevel attestationSecurityLevel;
  AndroidAttestationApplicationId attestationApplicationId;

  AndroidKeyAttestationExtension(
      byte[] attestationChallenge,
      AndroidKeyAttestationSecurityLevel attestationSecurityLevel,
      AndroidAttestationApplicationId attestationApplicationId) {
    this.attestationChallenge = attestationChallenge;
    this.attestationSecurityLevel = attestationSecurityLevel;
    this.attestationApplicationId = attestationApplicationId;
  }

  /**
   * Reads the extension of {@code leaf}.
   *
   * @throws AndroidKeyAttestationException when the certificate carries no extension, or the
   *     extension does not follow the schema
   */
  public static AndroidKeyAttestationExtension parse(X509Certificate leaf) {
    byte[] encoded = leaf.getExtensionValue(OID);
    if (encoded == null) {
      throw new AndroidKeyAttestationException(
          "leaf certificate has no key attestation extension (" + OID + ")");
    }

    try {
      Asn1Node keyDescription = Asn1Node.parseExtension(encoded);

      if (keyDescription.size() < KEY_DESCRIPTION_ELEMENTS) {
        throw new AndroidKeyAttestationException(
            "key attestation extension has "
                + keyDescription.size()
                + " elements, expected "
                + KEY_DESCRIPTION_ELEMENTS);
      }

      byte[] challenge = keyDescription.at(ATTESTATION_CHALLENGE_INDEX).octets();
      AndroidKeyAttestationSecurityLevel securityLevel =
          AndroidKeyAttestationSecurityLevel.of(
              keyDescription.at(ATTESTATION_SECURITY_LEVEL_INDEX).enumeratedValue());

      // The schema allows the field in either AuthorizationList, and it is the Android platform
      // rather than the secure hardware that records it, so softwareEnforced is where it lands.
      // hardwareEnforced is read as a fallback rather than assumed to be empty.
      AndroidAttestationApplicationId applicationId =
          findAttestationApplicationId(keyDescription.at(SOFTWARE_ENFORCED_INDEX))
              .or(() -> findAttestationApplicationId(keyDescription.at(HARDWARE_ENFORCED_INDEX)))
              .orElseThrow(
                  () ->
                      new AndroidKeyAttestationException(
                          "key attestation extension has no attestationApplicationId"));

      return new AndroidKeyAttestationExtension(challenge, securityLevel, applicationId);
    } catch (AndroidKeyAttestationException e) {
      throw e;
    } catch (Asn1InvalidException e) {
      throw new AndroidKeyAttestationException(
          "failed to parse key attestation extension: " + e.getMessage(), e);
    }
  }

  private static Optional<AndroidAttestationApplicationId> findAttestationApplicationId(
      Asn1Node authorizationList) {
    return authorizationList
        .findTagged(ATTESTATION_APPLICATION_ID_TAG)
        .map(tagged -> AndroidAttestationApplicationId.parse(tagged.taggedContent().octets()));
  }

  public byte[] attestationChallenge() {
    return attestationChallenge;
  }

  public AndroidKeyAttestationSecurityLevel attestationSecurityLevel() {
    return attestationSecurityLevel;
  }

  public List<String> packageNames() {
    return new ArrayList<>(attestationApplicationId.packageNames());
  }

  public List<String> signatureDigests() {
    return new ArrayList<>(attestationApplicationId.signatureDigests());
  }
}
