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

package org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials;

import java.util.Objects;
import org.idp.server.platform.jose.JsonWebKey;
import org.idp.server.platform.jose.JsonWebKeyType;

/**
 * ClientAuthenticationPublicKey
 *
 * <p>Represents the public key used for verifying client_assertion JWTs in private_key_jwt client
 * authentication. Provides access to key metadata including type, size, and algorithm.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc7517">RFC 7517 - JSON Web Key (JWK)</a>
 */
public class ClientAuthenticationPublicKey {

  JsonWebKey jsonWebKey;

  public ClientAuthenticationPublicKey() {}

  public ClientAuthenticationPublicKey(JsonWebKey jsonWebKey) {
    this.jsonWebKey = jsonWebKey;
  }

  public boolean exists() {
    return Objects.nonNull(jsonWebKey);
  }

  public boolean isRsa() {
    JsonWebKeyType jsonWebKeyType = jsonWebKey.keyType();
    return jsonWebKeyType.isRsa();
  }

  public boolean isEc() {
    JsonWebKeyType jsonWebKeyType = jsonWebKey.keyType();
    return jsonWebKeyType.isEc();
  }

  /**
   * Returns the key size in bits.
   *
   * <p>For RSA keys, this is the modulus size. For EC keys, this is the curve size. FAPI requires
   * RSA keys to be 2048 bits or larger, and EC keys to be 160 bits or larger.
   *
   * @return the key size in bits
   */
  public int size() {
    return jsonWebKey.size();
  }

  /**
   * Returns the algorithm specified in the JWK's "alg" parameter.
   *
   * <p>This should match the algorithm used in the client_assertion JWT signature.
   *
   * @return the algorithm identifier (e.g., "PS256", "ES256"), or null if not specified
   */
  public String algorithm() {
    return jsonWebKey.algorithm();
  }
}
