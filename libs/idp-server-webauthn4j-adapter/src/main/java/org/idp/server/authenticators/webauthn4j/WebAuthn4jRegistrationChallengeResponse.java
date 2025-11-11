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

package org.idp.server.authenticators.webauthn4j;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.idp.server.platform.json.JsonReadable;

/**
 * Represents the complete response for FIDO2/WebAuthn registration challenge.
 *
 * <p>This class encapsulates all required and recommended fields for the
 * PublicKeyCredentialCreationOptions dictionary as specified in the W3C WebAuthn specification.
 * This response is sent to the client to initiate credential creation.
 *
 * <p><b>WebAuthn Specification Reference:</b> W3C WebAuthn Level 2, Section 5.4
 * PublicKeyCredentialCreationOptions
 *
 * <p><b>Required Fields:</b>
 *
 * <ul>
 *   <li>challenge - Cryptographically random bytes to prevent replay attacks
 *   <li>rp - Relying Party information (ID and name)
 *   <li>user - User account information (ID, name, displayName)
 *   <li>pubKeyCredParams - Acceptable cryptographic algorithms
 * </ul>
 *
 * <p><b>Recommended Fields:</b>
 *
 * <ul>
 *   <li>timeout - Time in milliseconds for the operation
 *   <li>authenticatorSelection - Constraints on acceptable authenticators
 *   <li>attestation - Attestation conveyance preference
 * </ul>
 *
 * <p><b>Usage Example:</b>
 *
 * <pre>{@code
 * Fido2RegistrationChallengeResponse response =
 *     new Fido2RegistrationChallengeResponse(
 *         challenge,
 *         relyingParty,
 *         user,
 *         pubKeyCredParams,
 *         authenticatorSelection,
 *         "none",
 *         60000L
 *     );
 *
 * Map<String, Object> responseMap = response.toMap();
 * // Returns snake_case keys for JSON serialization
 * }</pre>
 *
 * @see <a
 *     href="https://www.w3.org/TR/webauthn-2/#dictdef-publickeycredentialcreationoptions">WebAuthn
 *     PublicKeyCredentialCreationOptions</a>
 * @see Fido2Challenge
 * @see Fido2RelyingParty
 * @see Fido2User
 * @see Fido2PubKeyCredParam
 * @see Fido2AuthenticatorSelection
 */
public class WebAuthn4jRegistrationChallengeResponse implements Serializable, JsonReadable {

  /** The challenge (required). */
  WebAuthn4jChallenge challenge;

  /** The relying party information (required). */
  WebAuthn4jRelyingParty rp;

  /** The user information (required). */
  WebAuthn4jUser user;

  /** The acceptable public key credential parameters (required). */
  List<WebAuthn4jPubKeyCredParam> pubKeyCredParams;

  /** The authenticator selection criteria (recommended). */
  WebAuthn4jAuthenticatorSelection authenticatorSelection;

  /**
   * The attestation conveyance preference (recommended). Values: "none", "indirect", "direct",
   * "enterprise".
   */
  String attestation;

  /** The timeout in milliseconds (recommended). */
  Long timeout;

  /** Default constructor for JSON deserialization. */
  public WebAuthn4jRegistrationChallengeResponse() {}

  /**
   * Constructs a WebAuthn4jRegistrationChallengeResponse with all fields.
   *
   * @param challenge the challenge
   * @param rp the relying party information
   * @param user the user information
   * @param pubKeyCredParams the acceptable public key credential parameters
   * @param authenticatorSelection the authenticator selection criteria
   * @param attestation the attestation conveyance preference
   * @param timeout the timeout in milliseconds
   */
  public WebAuthn4jRegistrationChallengeResponse(
      WebAuthn4jChallenge challenge,
      WebAuthn4jRelyingParty rp,
      WebAuthn4jUser user,
      List<WebAuthn4jPubKeyCredParam> pubKeyCredParams,
      WebAuthn4jAuthenticatorSelection authenticatorSelection,
      String attestation,
      Long timeout) {
    this.challenge = challenge;
    this.rp = rp;
    this.user = user;
    this.pubKeyCredParams = pubKeyCredParams;
    this.authenticatorSelection = authenticatorSelection;
    this.attestation = attestation;
    this.timeout = timeout;
  }

  /**
   * Creates a complete FIDO2 registration challenge response from configuration.
   *
   * <p>This factory method generates all required and recommended fields for
   * PublicKeyCredentialCreationOptions as per W3C WebAuthn specification.
   *
   * <p><b>Usage Example:</b>
   *
   * <pre>{@code
   * WebAuthn4jRegistrationChallengeResponse response =
   *     WebAuthn4jRegistrationChallengeResponse.create(
   *         challenge,
   *         user,
   *         config
   *     );
   * }</pre>
   *
   * @param challenge the WebAuthn4j challenge
   * @param user the user information
   * @param config the WebAuthn4j configuration containing all necessary parameters
   * @return a complete challenge response
   */
  public static WebAuthn4jRegistrationChallengeResponse create(
      WebAuthn4jChallenge challenge, WebAuthn4jUser user, WebAuthn4jConfiguration config) {

    WebAuthn4jRelyingParty rp = createRelyingParty(config.rpId(), config.rpName());
    List<WebAuthn4jPubKeyCredParam> pubKeyCredParams =
        createPubKeyCredParams(config.pubKeyCredAlgorithms());
    WebAuthn4jAuthenticatorSelection authenticatorSelection =
        createAuthenticatorSelection(
            config.authenticatorAttachment(),
            config.residentKey(),
            config.requireResidentKey(),
            config.userVerificationRequired());
    String attestation = getAttestationPreference(config.attestationPreference());
    Long effectiveTimeout = getTimeout(config.timeout());

    return new WebAuthn4jRegistrationChallengeResponse(
        challenge,
        rp,
        user,
        pubKeyCredParams,
        authenticatorSelection,
        attestation,
        effectiveTimeout);
  }

  /**
   * Creates a relying party information.
   *
   * @param rpId the RP identifier
   * @param rpName the RP display name (optional, defaults to rpId)
   * @return the WebAuthn4j relying party
   */
  private static WebAuthn4jRelyingParty createRelyingParty(String rpId, String rpName) {
    String effectiveRpName = rpName != null ? rpName : rpId;
    return new WebAuthn4jRelyingParty(rpId, effectiveRpName);
  }

  /**
   * Creates public key credential parameters.
   *
   * @param algorithms the list of COSE algorithm identifiers (optional)
   * @return the list of public key credential parameters
   */
  private static List<WebAuthn4jPubKeyCredParam> createPubKeyCredParams(List<Integer> algorithms) {
    List<Integer> effectiveAlgorithms = algorithms;

    // Default to ES256 and RS256 if not specified
    if (effectiveAlgorithms == null || effectiveAlgorithms.isEmpty()) {
      effectiveAlgorithms = List.of(-7, -257); // ES256, RS256
    }

    return effectiveAlgorithms.stream()
        .map(alg -> new WebAuthn4jPubKeyCredParam("public-key", alg))
        .collect(Collectors.toList());
  }

  /**
   * Creates authenticator selection criteria.
   *
   * @param authenticatorAttachment the authenticator attachment preference
   * @param residentKey the resident key requirement
   * @param requireResidentKey the deprecated resident key flag
   * @param userVerificationRequired whether user verification is required
   * @return the WebAuthn4j authenticator selection, or null if no constraints specified
   */
  private static WebAuthn4jAuthenticatorSelection createAuthenticatorSelection(
      String authenticatorAttachment,
      String residentKey,
      Boolean requireResidentKey,
      boolean userVerificationRequired) {

    String userVerification = userVerificationRequired ? "required" : "preferred";

    // Only create selection if at least one constraint is specified
    if (authenticatorAttachment == null
        && residentKey == null
        && (requireResidentKey == null || !requireResidentKey)
        && !userVerificationRequired) {
      return null;
    }

    return new WebAuthn4jAuthenticatorSelection(
        authenticatorAttachment, residentKey, requireResidentKey, userVerification);
  }

  /**
   * Gets attestation preference.
   *
   * @param attestationPreference the attestation preference (optional)
   * @return the attestation preference, defaults to "none"
   */
  private static String getAttestationPreference(String attestationPreference) {
    return attestationPreference != null ? attestationPreference : "none";
  }

  /**
   * Gets timeout.
   *
   * @param timeout the timeout in milliseconds (optional)
   * @return the timeout, defaults to 60000 (60 seconds)
   */
  private static Long getTimeout(Long timeout) {
    return timeout != null ? timeout : 60000L;
  }

  /**
   * Returns the challenge.
   *
   * @return the challenge
   */
  public WebAuthn4jChallenge challenge() {
    return challenge;
  }

  /**
   * Returns the relying party information.
   *
   * @return the relying party
   */
  public WebAuthn4jRelyingParty rp() {
    return rp;
  }

  /**
   * Returns the user information.
   *
   * @return the user
   */
  public WebAuthn4jUser user() {
    return user;
  }

  /**
   * Returns the acceptable public key credential parameters.
   *
   * @return the public key credential parameters
   */
  public List<WebAuthn4jPubKeyCredParam> pubKeyCredParams() {
    return pubKeyCredParams;
  }

  /**
   * Returns the authenticator selection criteria.
   *
   * @return the authenticator selection
   */
  public WebAuthn4jAuthenticatorSelection authenticatorSelection() {
    return authenticatorSelection;
  }

  /**
   * Returns the attestation conveyance preference.
   *
   * @return the attestation preference
   */
  public String attestation() {
    return attestation;
  }

  /**
   * Returns the timeout in milliseconds.
   *
   * @return the timeout
   */
  public Long timeout() {
    return timeout;
  }

  /**
   * Converts this object to a Map representation with camelCase keys as per WebAuthn specification.
   *
   * @return a Map containing all PublicKeyCredentialCreationOptions fields
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();

    // Required fields
    if (challenge != null) {
      map.put("challenge", challenge.challengeAsString());
    }
    if (rp != null) {
      map.put("rp", rp.toMap());
    }
    if (user != null) {
      map.put("user", user.toMap());
    }
    if (pubKeyCredParams != null) {
      map.put(
          "pubKeyCredParams",
          pubKeyCredParams.stream()
              .map(WebAuthn4jPubKeyCredParam::toMap)
              .collect(Collectors.toList()));
    }

    // Recommended fields
    if (authenticatorSelection != null) {
      map.put("authenticatorSelection", authenticatorSelection.toMap());
    }
    if (attestation != null) {
      map.put("attestation", attestation);
    }
    if (timeout != null) {
      map.put("timeout", timeout);
    }

    return map;
  }
}
