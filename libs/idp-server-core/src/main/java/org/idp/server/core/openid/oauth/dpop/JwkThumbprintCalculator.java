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

package org.idp.server.core.openid.oauth.dpop;

import org.idp.server.platform.jose.JsonWebKey;
import org.idp.server.platform.jose.JsonWebKeyInvalidException;

/**
 * JWK Thumbprint Calculator (RFC 7638).
 *
 * <p>Computes the JWK Thumbprint of a public key using SHA-256 hash algorithm. The thumbprint is
 * computed over the required members of the JWK representation in lexicographic order.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc7638">RFC 7638</a>
 */
public class JwkThumbprintCalculator {

  JsonWebKey jsonWebKey;

  public JwkThumbprintCalculator(JsonWebKey jsonWebKey) {
    this.jsonWebKey = jsonWebKey;
  }

  public JwkThumbprint calculate() {
    try {
      String thumbprint = jsonWebKey.thumbprintSha256();
      return new JwkThumbprint(thumbprint);
    } catch (JsonWebKeyInvalidException e) {
      throw new DPoPProofInvalidException("Failed to compute JWK Thumbprint: " + e.getMessage());
    }
  }
}
