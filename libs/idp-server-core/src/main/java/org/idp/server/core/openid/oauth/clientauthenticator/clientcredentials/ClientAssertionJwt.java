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

import java.util.Map;
import java.util.Objects;
import org.idp.server.platform.jose.JsonWebSignature;
import org.idp.server.platform.jose.JsonWebTokenClaims;

/**
 * ClientAssertionJwt
 *
 * <p>Represents the client_assertion JWT used in private_key_jwt client authentication. Contains
 * the parsed JWT including its claims and signature algorithm.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc7523">RFC 7523 - JWT Profile for OAuth 2.0 Client
 *     Authentication</a>
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#ClientAuthentication">OIDC
 *     Core - Client Authentication</a>
 */
public class ClientAssertionJwt {
  JsonWebSignature jsonWebSignature;

  public ClientAssertionJwt() {}

  public ClientAssertionJwt(JsonWebSignature jsonWebSignature) {
    this.jsonWebSignature = jsonWebSignature;
  }

  public JsonWebTokenClaims claims() {
    return jsonWebSignature.claims();
  }

  /**
   * Returns the raw payload JSON of the client assertion.
   *
   * <p>Use this when JSON type matters (e.g., FAPI 2.0 §5.3.2.1-2.8 strict {@code aud}-as-string
   * check) since {@link #claims()} normalizes audience to a list.
   */
  public Map<String, Object> rawPayload() {
    return jsonWebSignature.rawPayload();
  }

  /**
   * Returns a single claim value from the raw payload, preserving its original JSON type.
   *
   * <p>Unlike {@link #claims()} which goes through Nimbus normalization, this returns the value
   * exactly as it appeared on the wire (e.g., {@code aud} stays a {@code String} if it was sent as
   * a string, or a {@code List<String>} if it was sent as an array).
   *
   * @param claimName the JWT claim name
   * @return the raw value, or {@code null} if the claim is absent
   */
  public Object getFromRawPayload(String claimName) {
    return rawPayload().get(claimName);
  }

  /**
   * Returns {@code true} when the {@code aud} claim was sent as a JSON array on the wire.
   *
   * <p>Necessary because {@link #claims()} (Nimbus-backed) coerces a single-string {@code aud} to a
   * list, hiding the original JSON shape. FAPI 2.0 §5.3.2.1-2.8 requires {@code aud} to be a string
   * and rejects arrays.
   */
  public boolean isAudArray() {
    return getFromRawPayload("aud") instanceof java.util.List;
  }

  public String subject() {
    return claims().getSub();
  }

  public String iss() {
    return claims().getIss();
  }

  public boolean exists() {
    return Objects.nonNull(jsonWebSignature) && jsonWebSignature.exists();
  }

  /**
   * Returns the signing algorithm used for this client assertion JWT.
   *
   * <p>Common algorithms include PS256, ES256, RS256. FAPI profiles restrict this to PS256 or ES256
   * only.
   *
   * @return the algorithm identifier (e.g., "PS256", "ES256", "RS256")
   */
  public String algorithm() {
    return jsonWebSignature.algorithm();
  }
}
