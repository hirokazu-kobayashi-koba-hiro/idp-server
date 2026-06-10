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

import java.util.Objects;

/**
 * JWK Thumbprint value object (RFC 7638).
 *
 * <p>Represents the base64url-encoded SHA-256 hash of the JWK Thumbprint of the DPoP public key.
 * Used as the value of the "jkt" member in the "cnf" (Confirmation) claim for DPoP-bound access
 * tokens.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc7638">RFC 7638 - JSON Web Key (JWK)
 *     Thumbprint</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9449.html#section-6">RFC 9449 Section 6</a>
 */
public class JwkThumbprint {

  String value;

  public JwkThumbprint() {}

  public JwkThumbprint(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    JwkThumbprint that = (JwkThumbprint) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }
}
