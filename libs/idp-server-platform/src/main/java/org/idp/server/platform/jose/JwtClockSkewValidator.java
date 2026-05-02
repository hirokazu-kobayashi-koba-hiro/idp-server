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

package org.idp.server.platform.jose;

import org.idp.server.platform.date.SystemDateTime;

/**
 * Validates {@code iat} / {@code nbf} clock skew on incoming JWTs.
 *
 * <p>FAPI 2.0 Security Profile §5.3.2.1-2.13:
 *
 * <blockquote>
 *
 * "to accommodate clock offsets, shall accept JWTs with an iat or nbf timestamp between 0 and 10
 * seconds in the future but shall reject JWTs with an iat or nbf timestamp greater than 60 seconds
 * in the future".
 *
 * </blockquote>
 *
 * <p>The 60 second cap is also a sensible OAuth 2.0 / OIDC default — it bounds replay windows and
 * detects clients with badly skewed clocks — so this validator is invoked from every JWT entry
 * point (client_assertion, DPoP proof, Request Object) regardless of the active profile.
 *
 * @see <a href="https://openid.net/specs/fapi-security-profile-2_0.html#section-5.3.2.1">FAPI 2.0
 *     Security Profile §5.3.2.1</a>
 */
public class JwtClockSkewValidator {

  /** Maximum allowed future skew on iat/nbf, in seconds. */
  public static final long MAX_CLOCK_SKEW_SECONDS = 60;

  /**
   * Rejects JWTs whose {@code iat} or {@code nbf} is more than {@link #MAX_CLOCK_SKEW_SECONDS}
   * seconds in the future relative to the AS clock.
   *
   * @throws JwtClockSkewException if either claim is too far in the future
   */
  public static void validateIatNbf(JsonWebTokenClaims claims) {
    long nowMs = SystemDateTime.currentEpochMilliSecond();
    long maxFutureMs = nowMs + (MAX_CLOCK_SKEW_SECONDS * 1000L);

    if (claims.hasIat() && claims.getIat().getTime() > maxFutureMs) {
      throw new JwtClockSkewException(
          "iat is more than " + MAX_CLOCK_SKEW_SECONDS + " seconds in the future");
    }
    if (claims.hasNbf() && claims.getNbf().getTime() > maxFutureMs) {
      throw new JwtClockSkewException(
          "nbf is more than " + MAX_CLOCK_SKEW_SECONDS + " seconds in the future");
    }
  }

  private JwtClockSkewValidator() {}
}
