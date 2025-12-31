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

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * IdentityCookieToken
 *
 * <p>JWT payload for IDP_IDENTITY cookie. Contains session information for SSO. Similar to
 * Keycloak's IdentityCookieToken.
 */
public class IdentityCookieToken {

  private final String issuer;
  private final String subject;
  private final String opSessionId;
  private final Instant authTime;
  private final Instant issuedAt;
  private final Instant expiration;

  public IdentityCookieToken(
      String issuer,
      String subject,
      String opSessionId,
      Instant authTime,
      Instant issuedAt,
      Instant expiration) {
    this.issuer = issuer;
    this.subject = subject;
    this.opSessionId = opSessionId;
    this.authTime = authTime;
    this.issuedAt = issuedAt;
    this.expiration = expiration;
  }

  public static IdentityCookieToken create(
      String issuer, String subject, String opSessionId, Instant authTime, long maxAgeSeconds) {
    Instant now = Instant.now();
    return new IdentityCookieToken(
        issuer, subject, opSessionId, authTime, now, now.plusSeconds(maxAgeSeconds));
  }

  public String issuer() {
    return issuer;
  }

  public String subject() {
    return subject;
  }

  public String opSessionId() {
    return opSessionId;
  }

  public Instant authTime() {
    return authTime;
  }

  public Instant issuedAt() {
    return issuedAt;
  }

  public Instant expiration() {
    return expiration;
  }

  public boolean isExpired() {
    return Instant.now().isAfter(expiration);
  }

  public Map<String, Object> toClaimsMap() {
    Map<String, Object> claims = new HashMap<>();
    claims.put("iss", issuer);
    claims.put("sub", subject);
    claims.put("sid", opSessionId);
    claims.put("auth_time", authTime.getEpochSecond());
    claims.put("iat", issuedAt.getEpochSecond());
    claims.put("exp", expiration.getEpochSecond());
    claims.put("typ", "ID");
    return claims;
  }

  public static IdentityCookieToken fromClaimsMap(Map<String, Object> claims) {
    String issuer = (String) claims.get("iss");
    String subject = (String) claims.get("sub");
    String opSessionId = (String) claims.get("sid");
    long authTimeEpoch = ((Number) claims.get("auth_time")).longValue();
    long iatEpoch = ((Number) claims.get("iat")).longValue();
    long expEpoch = ((Number) claims.get("exp")).longValue();

    return new IdentityCookieToken(
        issuer,
        subject,
        opSessionId,
        Instant.ofEpochSecond(authTimeEpoch),
        Instant.ofEpochSecond(iatEpoch),
        Instant.ofEpochSecond(expEpoch));
  }
}
