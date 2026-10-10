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

package org.idp.server.core.openid.oauth.clientauthenticator;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Issue #1902: how long the jti of a client assertion is remembered. It is the last moment the
 * assertion could be accepted: its exp, or iat plus the maximum lifetime if that comes first.
 */
class ClientAssertionAcceptableUntilTest {

  private static final Duration MAX_LIFETIME = Duration.ofSeconds(60);
  private static final Instant IAT = Instant.parse("2026-10-10T00:00:00Z");

  @Test
  void endsAtIatPlusTheMaximumWhenExpIsFurther() {
    Instant acceptableUntil =
        ClientAuthenticationJwtValidatable.acceptableUntil(
            IAT.plusSeconds(3600), Optional.of(IAT), MAX_LIFETIME);

    assertEquals(IAT.plusSeconds(60), acceptableUntil);
  }

  @Test
  void endsAtExpWhenItComesFirst() {
    Instant acceptableUntil =
        ClientAuthenticationJwtValidatable.acceptableUntil(
            IAT.plusSeconds(30), Optional.of(IAT), MAX_LIFETIME);

    assertEquals(IAT.plusSeconds(30), acceptableUntil);
  }

  @Test
  void endsAtExpWithoutIat() {
    Instant exp = IAT.plusSeconds(45);

    assertEquals(
        exp,
        ClientAuthenticationJwtValidatable.acceptableUntil(exp, Optional.empty(), MAX_LIFETIME));
  }
}
