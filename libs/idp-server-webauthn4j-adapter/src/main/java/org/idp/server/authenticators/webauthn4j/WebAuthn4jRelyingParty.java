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
 * Represents the Relying Party (RP) information for FIDO2/WebAuthn operations.
 *
 * <p>This class encapsulates the RP identifier and human-readable name as specified in the W3C
 * WebAuthn specification. The RP information is used during credential creation to identify the
 * service requesting authentication.
 *
 * <p><b>WebAuthn Specification Reference:</b> W3C WebAuthn Level 2, Section 5.4.1
 * PublicKeyCredentialRpEntity
 *
 * <p><b>Usage Example:</b>
 *
 * <pre>{@code
 * Fido2RelyingParty rp = new Fido2RelyingParty(
 *     "example.com",
 *     "Example Corporation"
 * );
 * }</pre>
 *
 * @see <a href="https://www.w3.org/TR/webauthn-2/#dictdef-publickeycredentialrpentity">WebAuthn
 *     PublicKeyCredentialRpEntity</a>
 */
public class WebAuthn4jRelyingParty implements Serializable, JsonReadable {

  /** The RP ID, typically a valid domain string (e.g., "example.com"). */
  String id;

  /** A human-readable identifier for the relying party (e.g., "Example Corporation"). */
  String name;

  /** Default constructor for JSON deserialization. */
  public WebAuthn4jRelyingParty() {}

  /**
   * Constructs a Fido2RelyingParty with the specified RP ID and name.
   *
   * @param id the RP ID (typically a domain string)
   * @param name the human-readable RP name
   */
  public WebAuthn4jRelyingParty(String id, String name) {
    this.id = id;
    this.name = name;
  }

  /**
   * Returns the RP ID.
   *
   * @return the RP ID
   */
  public String id() {
    return id;
  }

  /**
   * Returns the human-readable RP name.
   *
   * @return the RP name
   */
  public String name() {
    return name;
  }

  /**
   * Converts this object to a Map representation with snake_case keys.
   *
   * @return a Map containing "id" and "name" fields
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("id", id);
    map.put("name", name);
    return map;
  }
}
