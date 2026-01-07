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

import java.util.Date;
import java.util.List;
import org.idp.server.core.openid.token.exception.TokenBadRequestException;
import org.idp.server.platform.jose.JsonWebTokenClaims;

/**
 * JwtBearerGrantVerifier
 *
 * <p>Verifies JWT Bearer assertion claims according to RFC 7523.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7523">RFC 7523</a>
 */
public class JwtBearerGrantVerifier {

  JsonWebTokenClaims claims;
  String expectedAudience;

  public JwtBearerGrantVerifier(JsonWebTokenClaims claims, String expectedAudience) {
    this.claims = claims;
    this.expectedAudience = expectedAudience;
  }

  public void verify() {
    verifyRequiredClaims();
    verifyAudience();
    verifyExpiration();
    verifyNotBefore();
    verifyIssuedAt();
  }

  /** RFC 7523 Section 3 - Required claims: iss, sub, aud, exp */
  void verifyRequiredClaims() {
    if (!claims.hasIss()) {
      throw new TokenBadRequestException(
          "invalid_grant", "JWT Bearer assertion must contain 'iss' claim");
    }
    if (!claims.hasSub()) {
      throw new TokenBadRequestException(
          "invalid_grant", "JWT Bearer assertion must contain 'sub' claim");
    }
    if (!claims.hasAud()) {
      throw new TokenBadRequestException(
          "invalid_grant", "JWT Bearer assertion must contain 'aud' claim");
    }
    if (!claims.hasExp()) {
      throw new TokenBadRequestException(
          "invalid_grant", "JWT Bearer assertion must contain 'exp' claim");
    }
  }

  /**
   * RFC 7523 Section 3 - The "aud" claim identifies the authorization server as an intended
   * audience.
   */
  void verifyAudience() {
    List<String> audiences = claims.getAud();
    boolean audienceMatches = audiences.stream().anyMatch(aud -> aud.equals(expectedAudience));
    if (!audienceMatches) {
      throw new TokenBadRequestException(
          "invalid_grant",
          String.format(
              "JWT Bearer assertion 'aud' claim does not match expected audience: %s",
              expectedAudience));
    }
  }

  /**
   * RFC 7523 Section 3 - The "exp" claim identifies the expiration time on or after which the JWT
   * MUST NOT be accepted for processing.
   */
  void verifyExpiration() {
    Date exp = claims.getExp();
    Date now = new Date();
    if (exp.before(now)) {
      throw new TokenBadRequestException("invalid_grant", "JWT Bearer assertion has expired");
    }
  }

  /**
   * RFC 7523 Section 3 - The "nbf" claim identifies the time before which the JWT MUST NOT be
   * accepted for processing.
   */
  void verifyNotBefore() {
    if (!claims.hasNbf()) {
      return;
    }
    Date nbf = claims.getNbf();
    Date now = new Date();
    if (now.before(nbf)) {
      throw new TokenBadRequestException(
          "invalid_grant", "JWT Bearer assertion is not yet valid (nbf claim)");
    }
  }

  /** Verify issued at time is not in the future (optional but recommended check). */
  void verifyIssuedAt() {
    if (!claims.hasIat()) {
      return;
    }
    Date iat = claims.getIat();
    Date now = new Date();
    long fiveMinutesInMillis = 5 * 60 * 1000;
    if (iat.after(new Date(now.getTime() + fiveMinutesInMillis))) {
      throw new TokenBadRequestException(
          "invalid_grant", "JWT Bearer assertion 'iat' claim is too far in the future");
    }
  }
}
