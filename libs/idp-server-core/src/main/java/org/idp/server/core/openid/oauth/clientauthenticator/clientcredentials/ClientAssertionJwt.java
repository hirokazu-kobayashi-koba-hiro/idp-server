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
