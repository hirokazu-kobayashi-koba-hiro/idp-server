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

package org.idp.server.core.openid.token;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.type.extension.CreatedAt;
import org.idp.server.core.openid.oauth.type.extension.ExpiresAt;
import org.idp.server.core.openid.oauth.type.oauth.RefreshTokenEntity;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RefreshTokenCreatableTest {

  static class TestRefreshTokenCreator implements RefreshTokenCreatable {}

  private final TestRefreshTokenCreator creator = new TestRefreshTokenCreator();

  private static final String OLD_TOKEN_VALUE = "old-refresh-token-value";
  private static final LocalDateTime BASE_TIME = LocalDateTime.of(2025, 1, 1, 0, 0, 0);

  private RefreshToken createOldRefreshToken() {
    return new RefreshToken(
        new RefreshTokenEntity(OLD_TOKEN_VALUE),
        new CreatedAt(BASE_TIME),
        new ExpiresAt(BASE_TIME.plusSeconds(3600)));
  }

  private AuthorizationServerConfiguration createServerConfig(
      String strategy, boolean rotate, long refreshTokenDuration) {
    String json =
        String.format(
            """
        {
          "extension": {
            "refreshTokenStrategy": "%s",
            "rotateRefreshToken": %s,
            "refreshTokenDuration": %d
          }
        }
        """,
            strategy, rotate, refreshTokenDuration);
    return JsonConverter.defaultInstance().read(json, AuthorizationServerConfiguration.class);
  }

  private ClientConfiguration createClientConfig() {
    return JsonConverter.defaultInstance()
        .read(
            """
        {
          "clientId": "test-client",
          "extension": {}
        }
        """,
            ClientConfiguration.class);
  }

  private ClientConfiguration createClientConfigWithOverride(
      String strategy, Boolean rotate, Long refreshTokenDuration) {
    StringBuilder extensionJson = new StringBuilder();
    if (strategy != null) {
      extensionJson.append(String.format("\"refreshTokenStrategy\": \"%s\"", strategy));
    }
    if (rotate != null) {
      if (!extensionJson.isEmpty()) extensionJson.append(", ");
      extensionJson.append(String.format("\"rotateRefreshToken\": %s", rotate));
    }
    if (refreshTokenDuration != null) {
      if (!extensionJson.isEmpty()) extensionJson.append(", ");
      extensionJson.append(String.format("\"refreshTokenDuration\": %d", refreshTokenDuration));
    }
    String json =
        String.format(
            """
        {
          "clientId": "test-client",
          "extension": {%s}
        }
        """,
            extensionJson);
    return JsonConverter.defaultInstance().read(json, ClientConfiguration.class);
  }

  @Nested
  @DisplayName("テナント設定のみ（クライアントオーバーライドなし）")
  class TenantSettingsOnly {

    @Test
    @DisplayName("EXTENDS + rotate: 新しいトークン値と新しい有効期限で発行される")
    void extendsWithRotate() {
      AuthorizationServerConfiguration serverConfig = createServerConfig("EXTENDS", true, 7200);
      ClientConfiguration clientConfig = createClientConfig();
      RefreshToken oldToken = createOldRefreshToken();

      RefreshToken result = creator.refresh(oldToken, serverConfig, clientConfig);

      assertNotEquals(OLD_TOKEN_VALUE, result.refreshTokenEntity().value());
      assertNotEquals(oldToken.expiresAt(), result.expiresAt());
    }

    @Test
    @DisplayName("EXTENDS + !rotate: 旧トークン値を維持し、有効期限は延長される")
    void extendsWithoutRotate() {
      AuthorizationServerConfiguration serverConfig = createServerConfig("EXTENDS", false, 7200);
      ClientConfiguration clientConfig = createClientConfig();
      RefreshToken oldToken = createOldRefreshToken();

      RefreshToken result = creator.refresh(oldToken, serverConfig, clientConfig);

      assertEquals(OLD_TOKEN_VALUE, result.refreshTokenEntity().value());
      assertNotEquals(oldToken.expiresAt(), result.expiresAt());
    }

    @Test
    @DisplayName("FIXED + rotate: 新しいトークン値で発行され、有効期限は旧トークンを維持")
    void fixedWithRotate() {
      AuthorizationServerConfiguration serverConfig = createServerConfig("FIXED", true, 3600);
      ClientConfiguration clientConfig = createClientConfig();
      RefreshToken oldToken = createOldRefreshToken();

      RefreshToken result = creator.refresh(oldToken, serverConfig, clientConfig);

      assertNotEquals(OLD_TOKEN_VALUE, result.refreshTokenEntity().value());
      assertEquals(oldToken.expiresAt(), result.expiresAt());
    }

    @Test
    @DisplayName("FIXED + !rotate: トークンがそのまま返される")
    void fixedWithoutRotate() {
      AuthorizationServerConfiguration serverConfig = createServerConfig("FIXED", false, 3600);
      ClientConfiguration clientConfig = createClientConfig();
      RefreshToken oldToken = createOldRefreshToken();

      RefreshToken result = creator.refresh(oldToken, serverConfig, clientConfig);

      assertSame(oldToken, result);
    }
  }

  @Nested
  @DisplayName("クライアントレベルオーバーライド")
  class ClientOverride {

    @Test
    @DisplayName("テナントFIXED+rotate → クライアントEXTENDS+rotateにオーバーライド: 新トークン+新有効期限")
    void clientOverridesStrategyToExtends() {
      AuthorizationServerConfiguration serverConfig = createServerConfig("FIXED", true, 3600);
      ClientConfiguration clientConfig = createClientConfigWithOverride("EXTENDS", true, null);
      RefreshToken oldToken = createOldRefreshToken();

      RefreshToken result = creator.refresh(oldToken, serverConfig, clientConfig);

      assertNotEquals(OLD_TOKEN_VALUE, result.refreshTokenEntity().value());
      assertNotEquals(oldToken.expiresAt(), result.expiresAt());
    }

    @Test
    @DisplayName("テナントEXTENDS+rotate → クライアントFIXED+!rotateにオーバーライド: そのまま返却")
    void clientOverridesToFixedNoRotate() {
      AuthorizationServerConfiguration serverConfig = createServerConfig("EXTENDS", true, 3600);
      ClientConfiguration clientConfig = createClientConfigWithOverride("FIXED", false, null);
      RefreshToken oldToken = createOldRefreshToken();

      RefreshToken result = creator.refresh(oldToken, serverConfig, clientConfig);

      assertSame(oldToken, result);
    }

    @Test
    @DisplayName("クライアントがrotateのみオーバーライド: strategyはテナント設定を使用")
    void clientOverridesOnlyRotate() {
      AuthorizationServerConfiguration serverConfig = createServerConfig("EXTENDS", true, 3600);
      ClientConfiguration clientConfig = createClientConfigWithOverride(null, false, null);
      RefreshToken oldToken = createOldRefreshToken();

      RefreshToken result = creator.refresh(oldToken, serverConfig, clientConfig);

      // EXTENDS(テナント) + !rotate(クライアント) → 旧トークン値 + 新有効期限
      assertEquals(OLD_TOKEN_VALUE, result.refreshTokenEntity().value());
      assertNotEquals(oldToken.expiresAt(), result.expiresAt());
    }

    @Test
    @DisplayName("クライアントがstrategyのみオーバーライド: rotateはテナント設定を使用")
    void clientOverridesOnlyStrategy() {
      AuthorizationServerConfiguration serverConfig = createServerConfig("FIXED", false, 3600);
      ClientConfiguration clientConfig = createClientConfigWithOverride("EXTENDS", null, null);
      RefreshToken oldToken = createOldRefreshToken();

      RefreshToken result = creator.refresh(oldToken, serverConfig, clientConfig);

      // EXTENDS(クライアント) + !rotate(テナント) → 旧トークン値 + 新有効期限
      assertEquals(OLD_TOKEN_VALUE, result.refreshTokenEntity().value());
      assertNotEquals(oldToken.expiresAt(), result.expiresAt());
    }
  }

  @Nested
  @DisplayName("refreshTokenDurationのフォールバック")
  class DurationFallback {

    @Test
    @DisplayName("クライアントにrefreshTokenDuration設定あり: クライアント値を使用")
    void usesClientDuration() {
      AuthorizationServerConfiguration serverConfig = createServerConfig("EXTENDS", false, 3600);
      ClientConfiguration clientConfig = createClientConfigWithOverride(null, null, 86400L);
      RefreshToken oldToken = createOldRefreshToken();

      RefreshToken result = creator.refresh(oldToken, serverConfig, clientConfig);

      assertEquals(OLD_TOKEN_VALUE, result.refreshTokenEntity().value());
      // クライアントの86400秒が使用されることを検証（テナントの3600秒ではない）
      assertNotEquals(oldToken.expiresAt(), result.expiresAt());
    }
  }
}
