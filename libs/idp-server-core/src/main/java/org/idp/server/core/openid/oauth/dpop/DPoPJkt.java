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
 * DPoP Key Thumbprint (jkt) value object.
 *
 * <p>Represents the {@code dpop_jkt} parameter received in the authorization request, which is the
 * base64url-encoded JWK SHA-256 thumbprint (RFC 7638) of the public key the client will use for
 * DPoP proofs at the token endpoint.
 *
 * <p>RFC 9449 §10 (Authorization Code Binding to a DPoP Key): the authorization server MUST bind
 * the issued authorization code to this thumbprint and reject token requests whose DPoP proof does
 * not match.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9449.html#section-10">RFC 9449 Section 10</a>
 */
public class DPoPJkt {
  String value;

  public DPoPJkt() {}

  public DPoPJkt(String value) {
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
    DPoPJkt that = (DPoPJkt) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }
}
