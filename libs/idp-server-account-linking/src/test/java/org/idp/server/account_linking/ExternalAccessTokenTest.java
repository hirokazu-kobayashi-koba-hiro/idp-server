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

package org.idp.server.account_linking;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExternalAccessTokenTest {

  static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 30, 12, 0, 0);

  @Test
  void handsOutOnlyTheAccessTokenAsATokenExchangeResponse() {
    Map<String, Object> response =
        new ExternalAccessToken("external-token", NOW.plusSeconds(300), "openid email")
            .toResponse(NOW);

    assertEquals(
        Map.of(
            "access_token", "external-token",
            "token_type", "Bearer",
            "issued_token_type", "urn:ietf:params:oauth:token-type:access_token",
            "expires_in", 300L,
            "scope", "openid email"),
        response);
  }

  @Test
  void neverCountsTheExpiryBelowZero() {
    assertEquals(
        0L,
        new ExternalAccessToken("external-token", NOW.minusSeconds(5), null)
            .toResponse(NOW)
            .get("expires_in"));
  }

  @Test
  void leavesOutWhatTheLinkDidNotKeep() {
    Map<String, Object> response =
        new ExternalAccessToken("external-token", null, "").toResponse(NOW);

    assertFalse(response.containsKey("expires_in"));
    assertFalse(response.containsKey("scope"));
  }
}
