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

package org.idp.server.core.openid.clientinstance;

import java.util.Objects;

/**
 * The RFC 7638 thumbprint of the Client Instance Key a token was issued to.
 *
 * <p>draft-ietf-oauth-attestation-based-client-auth Section 10.3 requires a refresh token issued
 * through {@code attest_jwt_client_auth} to be bound to the Client Instance rather than only to the
 * client. Several instances of one application share a {@code client_id}, so client authentication
 * alone does not say which of them is refreshing.
 *
 * <p>Kept apart from {@code JwkThumbprint}, which is the DPoP binding. That one also decides {@code
 * token_type}: a value there means the client presents DPoP proofs, while an attestation client
 * presents a Client Attestation PoP and its tokens stay Bearer. Reusing the field would make the
 * token claim a proof mechanism the client never uses.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc7638">RFC 7638 - JSON Web Key (JWK)
 *     Thumbprint</a>
 */
public class ClientInstanceThumbprint {

  String value;

  public ClientInstanceThumbprint() {}

  public ClientInstanceThumbprint(String value) {
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
    ClientInstanceThumbprint that = (ClientInstanceThumbprint) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }
}
