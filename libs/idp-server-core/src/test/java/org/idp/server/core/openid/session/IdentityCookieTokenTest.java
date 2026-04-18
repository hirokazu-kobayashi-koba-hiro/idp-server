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

package org.idp.server.core.openid.session;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IdentityCookieTokenTest {

  @Test
  void toClaimsMapContainsAllFields() {
    Instant authTime = Instant.parse("2026-04-18T10:00:00Z");
    Instant issuedAt = Instant.parse("2026-04-18T10:00:00Z");
    Instant expiration = Instant.parse("2026-04-18T11:00:00Z");

    IdentityCookieToken token =
        new IdentityCookieToken(
            "https://idp.example.com",
            "user-001",
            "ops_session-id",
            authTime,
            issuedAt,
            expiration);

    Map<String, Object> claims = token.toClaimsMap();

    assertEquals("https://idp.example.com", claims.get("iss"));
    assertEquals("user-001", claims.get("sub"));
    assertEquals("ops_session-id", claims.get("sid"));
    assertEquals(authTime.getEpochSecond(), claims.get("auth_time"));
    assertEquals(issuedAt.getEpochSecond(), claims.get("iat"));
    assertEquals(expiration.getEpochSecond(), claims.get("exp"));
    assertEquals("ID", claims.get("typ"));
  }

  @Test
  void fromClaimsMapRoundTrips() {
    Instant authTime = Instant.parse("2026-04-18T10:00:00Z");
    Instant issuedAt = Instant.parse("2026-04-18T10:00:00Z");
    Instant expiration = Instant.parse("2026-04-18T11:00:00Z");

    IdentityCookieToken original =
        new IdentityCookieToken(
            "https://idp.example.com",
            "user-001",
            "ops_session-id",
            authTime,
            issuedAt,
            expiration);

    Map<String, Object> claims = original.toClaimsMap();
    IdentityCookieToken restored = IdentityCookieToken.fromClaimsMap(claims);

    assertEquals(original.issuer(), restored.issuer());
    assertEquals(original.subject(), restored.subject());
    assertEquals(original.opSessionId(), restored.opSessionId());
    assertEquals(original.authTime(), restored.authTime());
    assertEquals(original.issuedAt(), restored.issuedAt());
    assertEquals(original.expiration(), restored.expiration());
  }

  @Test
  void isExpiredReturnsFalseForFutureExpiration() {
    IdentityCookieToken token =
        IdentityCookieToken.create(
            "https://idp.example.com", "user-001", "ops_session-id", Instant.now(), 3600);

    assertFalse(token.isExpired());
  }

  @Test
  void isExpiredReturnsTrueForPastExpiration() {
    Instant pastAuth = Instant.parse("2020-01-01T00:00:00Z");
    Instant pastIssued = Instant.parse("2020-01-01T00:00:00Z");
    Instant pastExpiration = Instant.parse("2020-01-01T01:00:00Z");

    IdentityCookieToken token =
        new IdentityCookieToken(
            "https://idp.example.com",
            "user-001",
            "ops_session-id",
            pastAuth,
            pastIssued,
            pastExpiration);

    assertTrue(token.isExpired());
  }
}
