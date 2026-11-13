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
package org.idp.server.platform.oauth.client;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.exception.InvalidConfigurationException;

/**
 * The claims of a client assertion (RFC 7523 §3, OpenID Connect Core §9).
 *
 * <p>{@code iss} and {@code sub} are the client, {@code aud} the token endpoint or the issuer. The
 * assertion lives {@value #LIFETIME_SECONDS} seconds and carries a fresh {@code jti}; spotting a
 * replayed one is the receiving server's job, so nothing is kept here.
 */
public class ClientAssertionClaims {

  static final long LIFETIME_SECONDS = 60;

  public static final String ASSERTION_TYPE =
      "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";

  Map<String, Object> claims;

  ClientAssertionClaims(Map<String, Object> claims) {
    this.claims = claims;
  }

  /**
   * @throws InvalidConfigurationException when the assertion is to be addressed to the issuer and
   *     no issuer is known
   */
  public static ClientAssertionClaims of(OutboundClientCredentials credentials, LocalDateTime now) {
    String audience = credentials.assertionAudience();
    if (audience == null || audience.isEmpty()) {
      throw new InvalidConfigurationException(
          "client_authentication.assertion_audience is issuer, but no issuer is configured");
    }
    long issuedAt = SystemDateTime.toEpochSecond(now);
    Map<String, Object> claims = new HashMap<>();
    claims.put("iss", credentials.clientId());
    claims.put("sub", credentials.clientId());
    claims.put("aud", audience);
    claims.put("jti", UUID.randomUUID().toString());
    claims.put("iat", issuedAt);
    claims.put("exp", issuedAt + LIFETIME_SECONDS);
    return new ClientAssertionClaims(claims);
  }

  public Map<String, Object> toMap() {
    return claims;
  }
}
