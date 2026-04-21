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

import java.util.Map;
import org.idp.server.core.openid.token.exception.TokenBadRequestException;
import org.idp.server.platform.jose.JsonWebTokenClaims;

/**
 * TokenExchangeGrantVerifier
 *
 * <p>Verifies JWT claims in token exchange subject_token. Delegates to JwtBearerGrantVerifier for
 * standard claim checks (iss, sub, aud, exp) and adds Token Exchange specific validations.
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
  JsonWebTokenClaims claims;

  public TokenExchangeGrantVerifier(JsonWebTokenClaims claims, String expectedAudience) {
    this.jwtBearerGrantVerifier = new JwtBearerGrantVerifier(claims, expectedAudience);
    this.claims = claims;
  }

  public void verify() {
    jwtBearerGrantVerifier.verify();
    verifyMayActClaimType();
  }

  /**
   * RFC 8693 Section 4.4 - may_act claim must be a JSON object if present.
   *
   * <p>Validates the structural type of may_act before it is used for authorization decisions in
   * TokenExchangeGrantService.verifyMayAct().
   */
  /**
   * RFC 8693 Section 4.4 - may_act claim must be a JSON object if present.
   *
   * <blockquote>
   *
   * The claim value is a JSON object, and members in the JSON object are claims that identify the
   * party that is asserted as being eligible to act for the party identified by the JWT containing
   * the claim.
   *
   * </blockquote>
   */
  private void verifyMayActClaimType() {
    if (!claims.contains("may_act")) {
      return;
    }
    Object mayAct = claims.toMap().get("may_act");
    if (!(mayAct instanceof Map)) {
      throw new TokenBadRequestException(
          "invalid_grant", "may_act claim must be a JSON object (RFC 8693 Section 4.4)");
    }
  }
}
