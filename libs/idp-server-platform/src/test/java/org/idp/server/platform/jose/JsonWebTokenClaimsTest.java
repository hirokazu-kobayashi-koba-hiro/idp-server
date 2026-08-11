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

import static org.junit.jupiter.api.Assertions.*;

import com.nimbusds.jwt.JWTClaimsSet;
import java.text.ParseException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pins the Nimbus null-valued-claim behavior that {@link JsonWebTokenClaims} depends on, and the
 * null-aware {@code hasXxx()} contract that guards every {@code guard-then-get} caller (#1776).
 *
 * <p>Nimbus ({@code nimbus-jose-jwt}) does not reject a claim whose JSON value is {@code null}: the
 * key is retained ({@code containsKey == true}) while the typed getter returns {@code null}. If
 * {@code hasXxx()} reported presence by key alone, callers would pass their {@code if
 * (!claims.hasXxx()) throw ...} guard and then dereference a null getter — an NPE reachable
 * unauthenticated via a self-signed DPoP proof. These tests fail if Nimbus starts rejecting null
 * claims (the assumption changes) or if {@code hasXxx()} regresses to key-presence semantics.
 */
class JsonWebTokenClaimsTest {

  private static JWTClaimsSet parse(String json) throws ParseException {
    return JWTClaimsSet.parse(json);
  }

  @Nested
  class NimbusNullClaimAssumption {

    @Test
    void nullValuedRegisteredClaimsAreParsedNotRejected() throws ParseException {
      // The behavior our fix is built on: null-valued claims survive parsing as key-present/
      // value-null. If a Nimbus upgrade changes this, revisit JsonWebTokenClaims.hasXxx().
      JWTClaimsSet exp = parse("{\"sub\":\"c1\",\"exp\":null}");
      assertTrue(exp.getClaims().containsKey("exp"));
      assertNull(exp.getExpirationTime());

      JWTClaimsSet iat = parse("{\"sub\":\"c1\",\"iat\":null}");
      assertTrue(iat.getClaims().containsKey("iat"));
      assertNull(iat.getIssueTime());

      JWTClaimsSet nbf = parse("{\"sub\":\"c1\",\"nbf\":null}");
      assertTrue(nbf.getClaims().containsKey("nbf"));
      assertNull(nbf.getNotBeforeTime());

      JWTClaimsSet jti = parse("{\"sub\":\"c1\",\"jti\":null}");
      assertTrue(jti.getClaims().containsKey("jti"));
      assertNull(jti.getJWTID());
    }
  }

  @Nested
  class HasXxxIsNullAware {

    @Test
    void returnsFalseForNullValuedClaims() throws ParseException {
      JsonWebTokenClaims claims =
          new JsonWebTokenClaims(
              parse(
                  "{\"iss\":null,\"sub\":null,\"aud\":null,\"nbf\":null,"
                      + "\"iat\":null,\"jti\":null,\"exp\":null}"));

      assertFalse(claims.hasIss());
      assertFalse(claims.hasSub());
      assertFalse(claims.hasAud());
      assertFalse(claims.hasNbf());
      assertFalse(claims.hasIat());
      assertFalse(claims.hasJti());
      assertFalse(claims.hasExp());
    }

    @Test
    void returnsFalseForAbsentClaims() throws ParseException {
      JsonWebTokenClaims claims = new JsonWebTokenClaims(parse("{\"sub\":\"c1\"}"));

      assertFalse(claims.hasIss());
      assertFalse(claims.hasAud());
      assertFalse(claims.hasNbf());
      assertFalse(claims.hasIat());
      assertFalse(claims.hasJti());
      assertFalse(claims.hasExp());
      assertTrue(claims.hasSub());
    }

    @Test
    void returnsTrueForPresentValues() throws ParseException {
      JsonWebTokenClaims claims =
          new JsonWebTokenClaims(
              parse(
                  "{\"iss\":\"c1\",\"sub\":\"c1\",\"aud\":[\"as\"],\"nbf\":1000,"
                      + "\"iat\":1000,\"jti\":\"j1\",\"exp\":2000}"));

      assertTrue(claims.hasIss());
      assertTrue(claims.hasSub());
      assertTrue(claims.hasAud());
      assertTrue(claims.hasNbf());
      assertTrue(claims.hasIat());
      assertTrue(claims.hasJti());
      assertTrue(claims.hasExp());
    }

    @Test
    void hasAudIsFalseForEmptyList() throws ParseException {
      JsonWebTokenClaims claims = new JsonWebTokenClaims(parse("{\"sub\":\"c1\",\"aud\":[]}"));

      assertFalse(claims.hasAud());
    }

    @Test
    void nullBackingValueIsHandled() {
      JsonWebTokenClaims claims = new JsonWebTokenClaims();

      assertFalse(claims.hasIss());
      assertFalse(claims.hasExp());
      assertFalse(claims.hasJti());
    }
  }
}
