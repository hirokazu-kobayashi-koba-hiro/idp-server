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
 * Represents a public key credential parameter for FIDO2/WebAuthn operations.
 *
 * <p>This class specifies the type of credential and the cryptographic algorithm to be used during
 * credential creation. Multiple parameters can be provided to indicate preference order and
 * fallback options.
 *
 * <p><b>WebAuthn Specification Reference:</b> W3C WebAuthn Level 2, Section 5.8.1
 * PublicKeyCredentialParameters
 *
 * <p><b>Common Algorithm Identifiers (COSE):</b>
 *
 * <ul>
 *   <li>ES256 (ECDSA w/ SHA-256): -7
 *   <li>ES384 (ECDSA w/ SHA-384): -35
 *   <li>ES512 (ECDSA w/ SHA-512): -36
 *   <li>RS256 (RSASSA-PKCS1-v1_5 w/ SHA-256): -257
 *   <li>RS384 (RSASSA-PKCS1-v1_5 w/ SHA-384): -258
 *   <li>RS512 (RSASSA-PKCS1-v1_5 w/ SHA-512): -259
 *   <li>PS256 (RSASSA-PSS w/ SHA-256): -37
 *   <li>PS384 (RSASSA-PSS w/ SHA-384): -38
 *   <li>PS512 (RSASSA-PSS w/ SHA-512): -39
 *   <li>EdDSA: -8
 * </ul>
 *
 * <p><b>Usage Example:</b>
 *
 * <pre>{@code
 * // Prefer ES256, fallback to RS256
 * List<Fido2PubKeyCredParam> params = List.of(
 *     new Fido2PubKeyCredParam("public-key", -7),   // ES256
 *     new Fido2PubKeyCredParam("public-key", -257)  // RS256
 * );
 * }</pre>
 *
 * @see <a href="https://www.w3.org/TR/webauthn-2/#dictdef-publickeycredentialparameters">WebAuthn
 *     PublicKeyCredentialParameters</a>
 * @see <a href="https://www.iana.org/assignments/cose/cose.xhtml">IANA COSE Algorithm Registry</a>
 */
public class WebAuthn4jPubKeyCredParam implements Serializable, JsonReadable {

  /** The type of credential. For WebAuthn, this is always "public-key". */
  String type;

  /**
   * The COSE algorithm identifier. Negative integers per IANA COSE Algorithm Registry. Common
   * values: -7 (ES256), -257 (RS256).
   */
  int alg;

  /** Default constructor for JSON deserialization. */
  public WebAuthn4jPubKeyCredParam() {}

  /**
   * Constructs a Fido2PubKeyCredParam with the specified type and algorithm.
   *
   * @param type the credential type (typically "public-key")
   * @param alg the COSE algorithm identifier (e.g., -7 for ES256)
   */
  public WebAuthn4jPubKeyCredParam(String type, int alg) {
    this.type = type;
    this.alg = alg;
  }

  /**
   * Returns the credential type.
   *
   * @return the type (typically "public-key")
   */
  public String type() {
    return type;
  }

  /**
   * Returns the COSE algorithm identifier.
   *
   * @return the algorithm identifier
   */
  public int alg() {
    return alg;
  }

  /**
   * Converts this object to a Map representation.
   *
   * @return a Map containing "type" and "alg" fields
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("type", type);
    map.put("alg", alg);
    return map;
  }
}
