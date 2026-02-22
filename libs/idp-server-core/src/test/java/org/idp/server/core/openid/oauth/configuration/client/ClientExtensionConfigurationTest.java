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

package org.idp.server.core.openid.oauth.configuration.client;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ClientExtensionConfigurationTest {

  private ClientExtensionConfiguration fromJson(String json) {
    return JsonConverter.defaultInstance().read(json, ClientExtensionConfiguration.class);
  }

  @Nested
  @DisplayName("rotateRefreshToken の null vs false の区別")
  class RotateRefreshTokenNullability {

    @Test
    @DisplayName("未設定(null): hasRotateRefreshToken=false, toMapに含まれない")
    void notSet() {
      ClientExtensionConfiguration config = fromJson("{}");

      assertFalse(config.hasRotateRefreshToken());

      Map<String, Object> map = config.toMap();
      assertFalse(map.containsKey("rotate_refresh_token"));
    }

    @Test
    @DisplayName("明示的にfalse: hasRotateRefreshToken=true, toMapにfalseとして含まれる")
    void explicitlyFalse() {
      ClientExtensionConfiguration config = fromJson("{\"rotateRefreshToken\": false}");

      assertTrue(config.hasRotateRefreshToken());
      assertFalse(config.isRotateRefreshToken());

      Map<String, Object> map = config.toMap();
      assertTrue(map.containsKey("rotate_refresh_token"));
      assertEquals(false, map.get("rotate_refresh_token"));
    }

    @Test
    @DisplayName("明示的にtrue: hasRotateRefreshToken=true, toMapにtrueとして含まれる")
    void explicitlyTrue() {
      ClientExtensionConfiguration config = fromJson("{\"rotateRefreshToken\": true}");

      assertTrue(config.hasRotateRefreshToken());
      assertTrue(config.isRotateRefreshToken());

      Map<String, Object> map = config.toMap();
      assertTrue(map.containsKey("rotate_refresh_token"));
      assertEquals(true, map.get("rotate_refresh_token"));
    }
  }

  @Nested
  @DisplayName("refreshTokenStrategy の null vs 空文字 の区別")
  class RefreshTokenStrategyNullability {

    @Test
    @DisplayName("未設定(null): hasRefreshTokenStrategy=false, toMapに含まれない")
    void notSet() {
      ClientExtensionConfiguration config = fromJson("{}");

      assertFalse(config.hasRefreshTokenStrategy());

      Map<String, Object> map = config.toMap();
      assertFalse(map.containsKey("refresh_token_strategy"));
    }

    @Test
    @DisplayName("設定あり: hasRefreshTokenStrategy=true, toMapに含まれる")
    void set() {
      ClientExtensionConfiguration config = fromJson("{\"refreshTokenStrategy\": \"EXTENDS\"}");

      assertTrue(config.hasRefreshTokenStrategy());
      assertTrue(config.refreshTokenStrategy().isExtends());

      Map<String, Object> map = config.toMap();
      assertTrue(map.containsKey("refresh_token_strategy"));
      assertEquals("EXTENDS", map.get("refresh_token_strategy"));
    }
  }

  @Nested
  @DisplayName("idTokenDuration の null vs 0 の区別")
  class IdTokenDurationNullability {

    @Test
    @DisplayName("未設定(null): hasIdTokenDuration=false, toMapに含まれない")
    void notSet() {
      ClientExtensionConfiguration config = fromJson("{}");

      assertFalse(config.hasIdTokenDuration());

      Map<String, Object> map = config.toMap();
      assertFalse(map.containsKey("id_token_duration"));
    }

    @Test
    @DisplayName("正の値: hasIdTokenDuration=true, toMapに含まれる")
    void positiveValue() {
      ClientExtensionConfiguration config = fromJson("{\"idTokenDuration\": 60}");

      assertTrue(config.hasIdTokenDuration());
      assertEquals(60, config.idTokenDuration());

      Map<String, Object> map = config.toMap();
      assertTrue(map.containsKey("id_token_duration"));
      assertEquals(60L, map.get("id_token_duration"));
    }

    @Test
    @DisplayName("0以下: hasIdTokenDuration=false, toMapに含まれない")
    void zeroValue() {
      ClientExtensionConfiguration config = fromJson("{\"idTokenDuration\": 0}");

      assertFalse(config.hasIdTokenDuration());

      Map<String, Object> map = config.toMap();
      assertFalse(map.containsKey("id_token_duration"));
    }
  }
}
