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
import java.util.Map;
import org.idp.server.platform.json.JsonReadable;

/**
 * Represents authenticator selection criteria for FIDO2/WebAuthn credential creation.
 *
 * <p>This class specifies the desired properties of authenticators that may be used for credential
 * creation. It allows the relying party to express preferences for platform vs. cross-platform
 * authenticators, resident key requirements, and user verification.
 *
 * <p><b>WebAuthn Specification Reference:</b> W3C WebAuthn Level 2, Section 5.4.4
 * AuthenticatorSelectionCriteria
 *
 * <p><b>Authenticator Attachment:</b>
 *
 * <ul>
 *   <li>"platform" - Platform authenticator (e.g., Touch ID, Windows Hello)
 *   <li>"cross-platform" - Roaming authenticator (e.g., USB security key)
 *   <li>null - No preference (either type acceptable)
 * </ul>
 *
 * <p><b>Resident Key:</b>
 *
 * <ul>
 *   <li>"discouraged" - Server prefers non-resident keys
 *   <li>"preferred" - Server prefers resident keys but accepts non-resident
 *   <li>"required" - Server requires resident keys
 * </ul>
 *
 * <p><b>User Verification:</b>
 *
 * <ul>
 *   <li>"required" - User verification must be performed
 *   <li>"preferred" - User verification preferred but not required
 *   <li>"discouraged" - User verification should not be performed
 * </ul>
 *
 * <p><b>Usage Example:</b>
 *
 * <pre>{@code
 * // Prefer platform authenticator with user verification
 * Fido2AuthenticatorSelection selection = new Fido2AuthenticatorSelection(
 *     "platform",
 *     "preferred",
 *     false,
 *     "preferred"
 * );
 * }</pre>
 *
 * @see <a href="https://www.w3.org/TR/webauthn-2/#dictdef-authenticatorselectioncriteria">WebAuthn
 *     AuthenticatorSelectionCriteria</a>
 */
public class WebAuthn4jAuthenticatorSelection implements Serializable, JsonReadable {

  /**
   * The authenticator attachment modality. Values: "platform", "cross-platform", or null for no
   * preference.
   */
  String authenticatorAttachment;

  /**
   * The resident key requirement. Values: "discouraged", "preferred", "required". Defaults to
   * "discouraged" if not specified.
   */
  String residentKey;

  /**
   * Deprecated field for resident key requirement. Replaced by residentKey. Retained for backward
   * compatibility.
   */
  Boolean requireResidentKey;

  /**
   * The user verification requirement. Values: "required", "preferred", "discouraged". Defaults to
   * "preferred" if not specified.
   */
  String userVerification;

  /** Default constructor for JSON deserialization. */
  public WebAuthn4jAuthenticatorSelection() {}

  /**
   * Constructs a Fido2AuthenticatorSelection with all parameters.
   *
   * @param authenticatorAttachment the authenticator attachment ("platform", "cross-platform", or
   *     null)
   * @param residentKey the resident key requirement ("discouraged", "preferred", "required")
   * @param requireResidentKey deprecated boolean resident key flag
   * @param userVerification the user verification requirement ("required", "preferred",
   *     "discouraged")
   */
  public WebAuthn4jAuthenticatorSelection(
      String authenticatorAttachment,
      String residentKey,
      Boolean requireResidentKey,
      String userVerification) {
    this.authenticatorAttachment = authenticatorAttachment;
    this.residentKey = residentKey;
    this.requireResidentKey = requireResidentKey;
    this.userVerification = userVerification;
  }

  /**
   * Returns the authenticator attachment preference.
   *
   * @return the authenticator attachment ("platform", "cross-platform", or null)
   */
  public String authenticatorAttachment() {
    return authenticatorAttachment;
  }

  /**
   * Returns the resident key requirement.
   *
   * @return the resident key requirement
   */
  public String residentKey() {
    return residentKey;
  }

  /**
   * Returns the deprecated resident key requirement flag.
   *
   * @return the requireResidentKey boolean
   * @deprecated Use {@link #residentKey()} instead
   */
  @Deprecated
  public Boolean requireResidentKey() {
    return requireResidentKey;
  }

  /**
   * Returns the user verification requirement.
   *
   * @return the user verification requirement
   */
  public String userVerification() {
    return userVerification;
  }

  /**
   * Converts this object to a Map representation with camelCase keys as per WebAuthn specification.
   *
   * @return a Map containing authenticator selection criteria
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    if (authenticatorAttachment != null) {
      map.put("authenticatorAttachment", authenticatorAttachment);
    }
    if (residentKey != null) {
      map.put("residentKey", residentKey);
    }
    if (requireResidentKey != null) {
      map.put("requireResidentKey", requireResidentKey);
    }
    if (userVerification != null) {
      map.put("userVerification", userVerification);
    }
    return map;
  }
}
