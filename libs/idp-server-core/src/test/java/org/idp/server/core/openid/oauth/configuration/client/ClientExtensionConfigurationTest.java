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

import java.util.List;
import java.util.Map;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ClientExtensionConfigurationTest {

  private ClientExtensionConfiguration fromJson(String json) {
    return JsonConverter.defaultInstance().read(json, ClientExtensionConfiguration.class);
  }

  private ClientExtensionConfiguration fromSnakeCaseJson(String json) {
    return JsonConverter.snakeCaseInstance().read(json, ClientExtensionConfiguration.class);
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

  @Nested
  @DisplayName("customProperties の保持")
  class CustomProperties {

    @Test
    @DisplayName("customPropertiesが設定されるとtoMapに含まれる")
    void withCustomProperties() {
      String json =
          "{\"customProperties\": {\"my_custom_param\": \"value\", \"custom_flag\": true}}";
      ClientExtensionConfiguration config = fromJson(json);

      assertTrue(config.hasCustomProperties());
      assertEquals("value", config.customProperties().get("my_custom_param"));
      assertEquals(true, config.customProperties().get("custom_flag"));

      Map<String, Object> map = config.toMap();
      assertTrue(map.containsKey("custom_properties"));
      @SuppressWarnings("unchecked")
      Map<String, Object> custom = (Map<String, Object>) map.get("custom_properties");
      assertEquals("value", custom.get("my_custom_param"));
      assertEquals(true, custom.get("custom_flag"));
    }

    @Test
    @DisplayName("snake_case変換経由でもcustom_propertiesが保持される")
    void withSnakeCaseConverter() {
      String json = "{\"custom_properties\": {\"my_param\": 42, \"nested\": {\"key\": \"val\"}}}";
      ClientExtensionConfiguration config = fromSnakeCaseJson(json);

      assertTrue(config.hasCustomProperties());
      assertEquals(42, config.customProperties().get("my_param"));
      @SuppressWarnings("unchecked")
      Map<String, Object> nested = (Map<String, Object>) config.customProperties().get("nested");
      assertEquals("val", nested.get("key"));
    }

    @Test
    @DisplayName("既知フィールドとcustomPropertiesが共存する")
    void coexistsWithKnownFields() {
      String json =
          "{\"accessTokenDuration\": 1800, \"customProperties\": {\"app_label\": \"test\"}}";
      ClientExtensionConfiguration config = fromJson(json);

      assertTrue(config.hasAccessTokenDuration());
      assertEquals(1800, config.accessTokenDuration());
      assertTrue(config.hasCustomProperties());
      assertEquals("test", config.customProperties().get("app_label"));

      Map<String, Object> map = config.toMap();
      assertEquals(1800L, map.get("access_token_duration"));
      assertTrue(map.containsKey("custom_properties"));
    }

    @Test
    @DisplayName("customPropertiesが空の場合toMapに含まれない")
    void emptyCustomProperties() {
      ClientExtensionConfiguration config = fromJson("{}");

      assertFalse(config.hasCustomProperties());

      Map<String, Object> map = config.toMap();
      assertFalse(map.containsKey("custom_properties"));
    }

    @Test
    @DisplayName("配列やネストされたオブジェクトも保持される")
    void nestedStructures() {
      String json =
          "{\"customProperties\": {\"tags\": [\"a\", \"b\"], \"metadata\": {\"level\": 1}}}";
      ClientExtensionConfiguration config = fromJson(json);

      assertTrue(config.hasCustomProperties());
      @SuppressWarnings("unchecked")
      List<String> tags = (List<String>) config.customProperties().get("tags");
      assertEquals(List.of("a", "b"), tags);
      @SuppressWarnings("unchecked")
      Map<String, Object> metadata =
          (Map<String, Object>) config.customProperties().get("metadata");
      assertEquals(1, metadata.get("level"));
    }
  }
}
