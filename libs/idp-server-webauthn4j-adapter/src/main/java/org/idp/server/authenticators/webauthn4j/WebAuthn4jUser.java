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
 * Represents the user information for FIDO2/WebAuthn credential creation.
 *
 * <p>This class encapsulates the user's unique identifier, account name, and display name as
 * specified in the W3C WebAuthn specification. The user information is used during credential
 * creation to associate the credential with a user account.
 *
 * <p><b>WebAuthn Specification Reference:</b> W3C WebAuthn Level 2, Section 5.4.3
 * PublicKeyCredentialUserEntity
 *
 * <p><b>Security Consideration:</b> The user ID should NOT contain personally identifiable
 * information (PII). It should be a unique, random identifier that cannot be used to identify the
 * user outside the context of the relying party.
 *
 * <p><b>Usage Example:</b>
 *
 * <pre>{@code
 * // Generate secure user ID (Base64URL encoded random bytes)
 * byte[] userIdBytes = new byte[32];
 * new SecureRandom().nextBytes(userIdBytes);
 * String userId = Base64.getUrlEncoder().withoutPadding().encodeToString(userIdBytes);
 *
 * Fido2User user = new Fido2User(
 *     userId,
 *     "user@example.com",
 *     "John Doe"
 * );
 * }</pre>
 *
 * @see <a href="https://www.w3.org/TR/webauthn-2/#dictdef-publickeycredentialuserentity">WebAuthn
 *     PublicKeyCredentialUserEntity</a>
 */
public class WebAuthn4jUser implements Serializable, JsonReadable {

  /**
   * The user handle - a unique identifier for the user account. MUST NOT contain PII. Base64URL
   * encoded.
   */
  String id;

  /** The user's account identifier, typically an email address or username. */
  String name;

  /** A human-friendly name for the user account, suitable for display. */
  String displayName;

  /** Default constructor for JSON deserialization. */
  public WebAuthn4jUser() {}

  /**
   * Constructs a Fido2User with the specified user ID, name, and display name.
   *
   * @param id the user handle (Base64URL encoded, must not contain PII)
   * @param name the user's account identifier (e.g., email address)
   * @param displayName the human-friendly display name
   */
  public WebAuthn4jUser(String id, String name, String displayName) {
    this.id = id;
    this.name = name;
    this.displayName = displayName;
  }

  /**
   * Returns the user handle.
   *
   * @return the user ID (Base64URL encoded)
   */
  public String id() {
    return id;
  }

  /**
   * Returns the user's account identifier.
   *
   * @return the user name
   */
  public String name() {
    return name;
  }

  /**
   * Returns the human-friendly display name.
   *
   * @return the display name
   */
  public String displayName() {
    return displayName;
  }

  /**
   * Converts this object to a Map representation with camelCase keys as per WebAuthn specification.
   *
   * @return a Map containing "id", "name", and "displayName" fields
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("id", id);
    map.put("name", name);
    map.put("displayName", displayName);
    return map;
  }
}
