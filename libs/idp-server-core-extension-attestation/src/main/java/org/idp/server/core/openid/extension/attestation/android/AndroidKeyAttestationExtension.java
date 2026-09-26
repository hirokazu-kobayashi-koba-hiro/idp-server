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
 * <p><b>Which AuthorizationList a field is read from is part of what it means.</b> {@code
 * softwareEnforced} is written by the Android platform and {@code hardwareEnforced} by KeyMint
 * inside the secure hardware, so a property of the key itself — {@code origin}, {@code purpose} —
 * is only a statement about the key when it comes from the hardware list. Reading either list for
 * those would accept the platform's word for something the platform is not in a position to know,
 * which is the whole reason the two lists exist separately. {@code attestationApplicationId} is the
 * opposite case and is read the other way round: it is the platform that knows which app asked.
 *
 * <p>Fields declared OPTIONAL by the schema are parsed into an {@code undefined} value rather than
 * rejected here, so that "the schema was not followed" and "the device did not report this" stay
 * distinguishable. Deciding that an absent {@code origin} is unacceptable is the verifier's call.
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

  /** Tags of the AuthorizationList entries this verifier reads. */
  private static final int PURPOSE_TAG = 1;

  private static final int ORIGIN_TAG = 702;
  private static final int ROOT_OF_TRUST_TAG = 704;
  private static final int OS_PATCH_LEVEL_TAG = 706;
  private static final int ATTESTATION_APPLICATION_ID_TAG = 709;

  private static final int ATTESTATION_SECURITY_LEVEL_INDEX = 1;
  private static final int KEY_MINT_SECURITY_LEVEL_INDEX = 3;
  private static final int ATTESTATION_CHALLENGE_INDEX = 4;
  private static final int SOFTWARE_ENFORCED_INDEX = 6;
  private static final int HARDWARE_ENFORCED_INDEX = 7;
  private static final int KEY_DESCRIPTION_ELEMENTS = 8;

  private static final int DEVICE_LOCKED_INDEX = 1;
  private static final int VERIFIED_BOOT_STATE_INDEX = 2;
  private static final int ROOT_OF_TRUST_ELEMENTS = 3;

  byte[] attestationChallenge;
  AndroidKeyAttestationSecurityLevel attestationSecurityLevel;
  AndroidKeyAttestationSecurityLevel keyMintSecurityLevel;
  AndroidKeyOrigin origin;
  List<AndroidKeyPurpose> purposes;
  AndroidRootOfTrust rootOfTrust;
  int osPatchLevel;
  AndroidAttestationApplicationId attestationApplicationId;

  AndroidKeyAttestationExtension(
      byte[] attestationChallenge,
      AndroidKeyAttestationSecurityLevel attestationSecurityLevel,
      AndroidKeyAttestationSecurityLevel keyMintSecurityLevel,
      AndroidKeyOrigin origin,
      List<AndroidKeyPurpose> purposes,
      AndroidRootOfTrust rootOfTrust,
      int osPatchLevel,
      AndroidAttestationApplicationId attestationApplicationId) {
    this.attestationChallenge = attestationChallenge;
    this.attestationSecurityLevel = attestationSecurityLevel;
    this.keyMintSecurityLevel = keyMintSecurityLevel;
    this.origin = origin;
    this.purposes = purposes;
    this.rootOfTrust = rootOfTrust;
    this.osPatchLevel = osPatchLevel;
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
      AndroidKeyAttestationSecurityLevel keyMintSecurityLevel =
          AndroidKeyAttestationSecurityLevel.of(
              keyDescription.at(KEY_MINT_SECURITY_LEVEL_INDEX).enumeratedValue());

      Asn1Node hardwareEnforced = keyDescription.at(HARDWARE_ENFORCED_INDEX);
      AndroidKeyOrigin origin = origin(hardwareEnforced);
      List<AndroidKeyPurpose> purposes = purposes(hardwareEnforced);
      AndroidRootOfTrust rootOfTrust = rootOfTrust(hardwareEnforced);
      int osPatchLevel = osPatchLevel(hardwareEnforced);

      // The schema allows the field in either AuthorizationList, and it is the Android platform
      // rather than the secure hardware that records it, so softwareEnforced is where it lands.
      // hardwareEnforced is read as a fallback rather than assumed to be empty.
      AndroidAttestationApplicationId applicationId =
          findAttestationApplicationId(keyDescription.at(SOFTWARE_ENFORCED_INDEX))
              .or(() -> findAttestationApplicationId(hardwareEnforced))
              .orElseThrow(
                  () ->
                      new AndroidKeyAttestationException(
                          "key attestation extension has no attestationApplicationId"));

      return new AndroidKeyAttestationExtension(
          challenge,
          securityLevel,
          keyMintSecurityLevel,
          origin,
          purposes,
          rootOfTrust,
          osPatchLevel,
          applicationId);
    } catch (AndroidKeyAttestationException e) {
      throw e;
    } catch (Asn1InvalidException e) {
      throw new AndroidKeyAttestationException(
          "failed to parse key attestation extension: " + e.getMessage(), e);
    }
  }

  /**
   * {@code origin} of the hardware list, or {@code undefined} when the device did not report it.
   */
  private static AndroidKeyOrigin origin(Asn1Node hardwareEnforced) {
    return hardwareEnforced
        .findTagged(ORIGIN_TAG)
        .map(tagged -> AndroidKeyOrigin.of(tagged.taggedContent().intValue()))
        .orElse(AndroidKeyOrigin.undefined);
  }

  /** {@code purpose} of the hardware list, empty when the device did not report it. */
  private static List<AndroidKeyPurpose> purposes(Asn1Node hardwareEnforced) {
    Optional<Asn1Node> tagged = hardwareEnforced.findTagged(PURPOSE_TAG);
    if (tagged.isEmpty()) {
      return List.of();
    }
    List<AndroidKeyPurpose> purposes = new ArrayList<>();
    for (Asn1Node element : tagged.get().taggedContent().elements()) {
      purposes.add(AndroidKeyPurpose.of(element.intValue()));
    }
    return purposes;
  }

  /**
   * {@code rootOfTrust} of the hardware list, or a not reported one when the device omitted it.
   *
   * <p>Read from the hardware list only: the boot state is what decides whether the platform's
   * statements can be believed, so a copy the platform wrote itself would prove nothing.
   */
  private static AndroidRootOfTrust rootOfTrust(Asn1Node hardwareEnforced)
      throws Asn1InvalidException {
    Optional<Asn1Node> tagged = hardwareEnforced.findTagged(ROOT_OF_TRUST_TAG);
    if (tagged.isEmpty()) {
      return AndroidRootOfTrust.notReported();
    }
    Asn1Node rootOfTrust = tagged.get().taggedContent();
    if (rootOfTrust.size() < ROOT_OF_TRUST_ELEMENTS) {
      throw new AndroidKeyAttestationException(
          "rootOfTrust has "
              + rootOfTrust.size()
              + " elements, expected at least "
              + ROOT_OF_TRUST_ELEMENTS);
    }
    return AndroidRootOfTrust.of(
        rootOfTrust.at(DEVICE_LOCKED_INDEX).booleanValue(),
        AndroidVerifiedBootState.of(rootOfTrust.at(VERIFIED_BOOT_STATE_INDEX).enumeratedValue()));
  }

  /** {@code osPatchLevel} of the hardware list as YYYYMM, or 0 when the device omitted it. */
  private static int osPatchLevel(Asn1Node hardwareEnforced) throws Asn1InvalidException {
    Optional<Asn1Node> tagged = hardwareEnforced.findTagged(OS_PATCH_LEVEL_TAG);
    if (tagged.isEmpty()) {
      return 0;
    }
    return tagged.get().taggedContent().intValue();
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

  /**
   * Where the KeyMint that holds the key runs, as opposed to where the attestation was produced.
   *
   * <p>The two are separate fields because they can differ, and it is this one that decides whether
   * the hardware list means anything.
   */
  public AndroidKeyAttestationSecurityLevel keyMintSecurityLevel() {
    return keyMintSecurityLevel;
  }

  public AndroidKeyOrigin origin() {
    return origin;
  }

  public List<AndroidKeyPurpose> purposes() {
    return new ArrayList<>(purposes);
  }

  public AndroidRootOfTrust rootOfTrust() {
    return rootOfTrust;
  }

  /** The OS security patch level as YYYYMM, or 0 when the device did not report it. */
  public int osPatchLevel() {
    return osPatchLevel;
  }

  public boolean hasOsPatchLevel() {
    return osPatchLevel > 0;
  }

  public List<String> packageNames() {
    return new ArrayList<>(attestationApplicationId.packageNames());
  }

  public List<String> signatureDigests() {
    return new ArrayList<>(attestationApplicationId.signatureDigests());
  }
}
