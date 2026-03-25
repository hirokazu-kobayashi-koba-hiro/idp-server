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

package org.idp.server.core.openid.token.verifier;

import org.idp.server.platform.jose.JsonWebTokenClaims;

/**
 * TokenExchangeGrantVerifier
 *
 * <p>Verifies JWT claims in token exchange subject_token. Delegates to JwtBearerGrantVerifier since
 * the same RFC 7523 claim checks apply (iss, sub, aud, exp).
 *
 * <p>RFC 8693 Section 2.1:
 *
 * <blockquote>
 *
 * The authorization server MUST perform the appropriate validation procedures for the indicated
 * token type and, if the actor token is present, also perform the appropriate validation procedures
 * for its indicated token type.
 *
 * </blockquote>
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693#section-2.1">RFC 8693 Section 2.1</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7523">RFC 7523 - JWT Bearer</a>
 */
public class TokenExchangeGrantVerifier {

  JwtBearerGrantVerifier jwtBearerGrantVerifier;

  public TokenExchangeGrantVerifier(JsonWebTokenClaims claims, String expectedAudience) {
    this.jwtBearerGrantVerifier = new JwtBearerGrantVerifier(claims, expectedAudience);
  }

  public void verify() {
    jwtBearerGrantVerifier.verify();
  }
}
