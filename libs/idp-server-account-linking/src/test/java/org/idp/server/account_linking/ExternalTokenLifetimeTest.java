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
import org.idp.server.federation.sso.oidc.OidcSsoConfiguration;
import org.idp.server.federation.sso.oidc.OidcTokenResult;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExternalTokenLifetimeTest {

  static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 12, 0);

  static OidcSsoConfiguration configuration(String json) {
    return JsonConverter.snakeCaseInstance().read(json, OidcSsoConfiguration.class);
  }

  @Test
  @DisplayName("expires_in があればそれを使う")
  void usesExpiresIn() {
    OidcTokenResult result = new OidcTokenResult(200, Map.of(), Map.of("expires_in", 600));
    assertEquals(NOW.plusSeconds(600), ExternalTokenLifetime.accessTokenExpiresAt(result, NOW));
  }

  @Test
  @DisplayName("expires_in が無ければ 1 時間とみなす（連携時もリフレッシュ時も同じ）")
  void assumesAnHourWithoutExpiresIn() {
    OidcTokenResult result = new OidcTokenResult(200, Map.of(), Map.of("access_token", "a"));
    assertEquals(NOW.plusSeconds(3600), ExternalTokenLifetime.accessTokenExpiresAt(result, NOW));
  }

  @Test
  @DisplayName("refresh_token_expires_in があれば期限にする")
  void usesConfiguredRefreshLifetime() {
    assertEquals(
        NOW.plusSeconds(86400),
        ExternalTokenLifetime.refreshTokenExpiresAt(
            configuration("{\"refresh_token_expires_in\": 86400}"), NOW));
  }

  @Test
  @DisplayName("refresh_token_expires_in が無ければ期限は不明（いま期限切れにはしない）")
  void leavesRefreshExpiryUnknownWithoutConfiguration() {
    assertNull(ExternalTokenLifetime.refreshTokenExpiresAt(configuration("{}"), NOW));
  }
}
