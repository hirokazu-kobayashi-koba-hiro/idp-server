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

package org.idp.server.control_plane.management.token.handler;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.management.token.io.TokenCreateRequest;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.type.extension.CreatedAt;
import org.idp.server.core.openid.oauth.type.extension.ExpiresAt;
import org.idp.server.core.openid.token.RefreshToken;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AdminTokenFactoryTest {

  AdminTokenFactory factory = new AdminTokenFactory();
  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Nested
  @DisplayName("resolveUseJwt")
  class ResolveUseJwtTest {

    @Test
    @DisplayName("リクエストでjwt指定 → サーバー設定に関係なくtrue")
    void requestJwt_returnsTrue() {
      Map<String, Object> body = new HashMap<>();
      body.put("client_id", "test");
      body.put("scopes", "openid");
      body.put("token_format", "jwt");
      TokenCreateRequest request = new TokenCreateRequest(body);

      AuthorizationServerConfiguration serverConfig = createServerConfig(true);

      assertTrue(factory.resolveUseJwt(request, serverConfig));
    }

    @Test
    @DisplayName("リクエストでopaque指定 → サーバー設定に関係なくfalse")
    void requestOpaque_returnsFalse() {
      Map<String, Object> body = new HashMap<>();
      body.put("client_id", "test");
      body.put("scopes", "openid");
      body.put("token_format", "opaque");
      TokenCreateRequest request = new TokenCreateRequest(body);

      AuthorizationServerConfiguration serverConfig = createServerConfig(false);

      assertFalse(factory.resolveUseJwt(request, serverConfig));
    }

    @Test
    @DisplayName("token_format未指定 → サーバー設定がJWTならtrue")
    void noFormat_serverJwt_returnsTrue() {
      Map<String, Object> body = new HashMap<>();
      body.put("client_id", "test");
      body.put("scopes", "openid");
      TokenCreateRequest request = new TokenCreateRequest(body);

      AuthorizationServerConfiguration serverConfig = createServerConfig(false);

      assertTrue(factory.resolveUseJwt(request, serverConfig));
    }

    @Test
    @DisplayName("token_format未指定 → サーバー設定がIdentifier(opaque)ならfalse")
    void noFormat_serverIdentifier_returnsFalse() {
      Map<String, Object> body = new HashMap<>();
      body.put("client_id", "test");
      body.put("scopes", "openid");
      TokenCreateRequest request = new TokenCreateRequest(body);

      AuthorizationServerConfiguration serverConfig = createServerConfig(true);

      assertFalse(factory.resolveUseJwt(request, serverConfig));
    }
  }

  @Nested
  @DisplayName("resolveAccessTokenDuration")
  class ResolveAccessTokenDurationTest {

    @Test
    @DisplayName("リクエスト指定 → リクエスト値が最優先")
    void requestDuration_takesPrecedence() {
      Map<String, Object> body = new HashMap<>();
      body.put("client_id", "test");
      body.put("scopes", "openid");
      body.put("access_token_duration", 600);
      TokenCreateRequest request = new TokenCreateRequest(body);

      ClientConfiguration clientConfig = createClientConfig(1800);
      AuthorizationServerConfiguration serverConfig = createServerConfig(3600);

      assertEquals(600, factory.resolveAccessTokenDuration(request, clientConfig, serverConfig));
    }

    @Test
    @DisplayName("リクエスト未指定 → クライアント設定が次に優先")
    void noRequestDuration_clientTakesPrecedence() {
      Map<String, Object> body = new HashMap<>();
      body.put("client_id", "test");
      body.put("scopes", "openid");
      TokenCreateRequest request = new TokenCreateRequest(body);

      ClientConfiguration clientConfig = createClientConfig(1800);
      AuthorizationServerConfiguration serverConfig = createServerConfig(3600);

      assertEquals(1800, factory.resolveAccessTokenDuration(request, clientConfig, serverConfig));
    }

    @Test
    @DisplayName("リクエスト・クライアント未指定 → サーバー設定にフォールバック")
    void noRequestNoClient_serverFallback() {
      Map<String, Object> body = new HashMap<>();
      body.put("client_id", "test");
      body.put("scopes", "openid");
      TokenCreateRequest request = new TokenCreateRequest(body);

      ClientConfiguration clientConfig = createClientConfig(0);
      AuthorizationServerConfiguration serverConfig = createServerConfig(3600);

      assertEquals(3600, factory.resolveAccessTokenDuration(request, clientConfig, serverConfig));
    }
  }

  @Nested
  @DisplayName("shouldCreateRefreshToken")
  class ShouldCreateRefreshTokenTest {

    @Test
    @DisplayName("user_idあり・duration未指定 → true")
    void withUserId_noExplicitDuration_returnsTrue() {
      Map<String, Object> body = new HashMap<>();
      body.put("client_id", "test");
      body.put("scopes", "openid");
      body.put("user_id", "user-123");
      TokenCreateRequest request = new TokenCreateRequest(body);

      assertTrue(factory.shouldCreateRefreshToken(request));
    }

    @Test
    @DisplayName("user_idなし → false（client_credentials相当）")
    void noUserId_returnsFalse() {
      Map<String, Object> body = new HashMap<>();
      body.put("client_id", "test");
      body.put("scopes", "openid");
      TokenCreateRequest request = new TokenCreateRequest(body);

      assertFalse(factory.shouldCreateRefreshToken(request));
    }

    @Test
    @DisplayName("user_idあり・duration=0 → false（明示的に無効化）")
    void withUserId_durationZero_returnsFalse() {
      Map<String, Object> body = new HashMap<>();
      body.put("client_id", "test");
      body.put("scopes", "openid");
      body.put("user_id", "user-123");
      body.put("refresh_token_duration", 0);
      TokenCreateRequest request = new TokenCreateRequest(body);

      assertFalse(factory.shouldCreateRefreshToken(request));
    }

    @Test
    @DisplayName("user_idあり・duration指定あり → true")
    void withUserId_withDuration_returnsTrue() {
      Map<String, Object> body = new HashMap<>();
      body.put("client_id", "test");
      body.put("scopes", "openid");
      body.put("user_id", "user-123");
      body.put("refresh_token_duration", 86400);
      TokenCreateRequest request = new TokenCreateRequest(body);

      assertTrue(factory.shouldCreateRefreshToken(request));
    }
  }

  @Nested
  @DisplayName("buildJwtPayload")
  class BuildJwtPayloadTest {

    @Test
    @DisplayName("custom_claimsがJWTペイロードに含まれる")
    void customClaims_includedInPayload() {
      Map<String, Object> body = new HashMap<>();
      body.put("client_id", "my-client");
      body.put("scopes", "openid profile");
      body.put("custom_claims", Map.of("role", "admin", "department", "engineering"));
      TokenCreateRequest request = new TokenCreateRequest(body);

      AuthorizationServerConfiguration serverConfig = createServerConfig(3600);
      LocalDateTime now = LocalDateTime.of(2026, 3, 19, 12, 0, 0);
      CreatedAt createdAt = new CreatedAt(now);
      ExpiresAt expiresAt = new ExpiresAt(now.plusSeconds(3600));

      Map<String, Object> payload =
          factory.buildJwtPayload(
              request,
              serverConfig,
              "my-client",
              "openid profile",
              new User(),
              createdAt,
              expiresAt);

      assertEquals("admin", payload.get("role"));
      assertEquals("engineering", payload.get("department"));
    }

    @Test
    @DisplayName("標準クレームがcustom_claimsで上書きできない")
    void standardClaims_cannotBeOverwritten() {
      Map<String, Object> body = new HashMap<>();
      body.put("client_id", "my-client");
      body.put("scopes", "openid");
      body.put("custom_claims", Map.of("iss", "evil-issuer", "exp", 9999999999L));
      TokenCreateRequest request = new TokenCreateRequest(body);

      AuthorizationServerConfiguration serverConfig = createServerConfig(3600);
      LocalDateTime now = LocalDateTime.of(2026, 3, 19, 12, 0, 0);
      CreatedAt createdAt = new CreatedAt(now);
      ExpiresAt expiresAt = new ExpiresAt(now.plusSeconds(3600));

      Map<String, Object> payload =
          factory.buildJwtPayload(
              request, serverConfig, "my-client", "openid", new User(), createdAt, expiresAt);

      // iss must be the server's issuer, not the custom claim
      assertNotEquals("evil-issuer", payload.get("iss"));
      // exp must match the calculated expiry, not the custom claim
      assertNotEquals(9999999999L, payload.get("exp"));
    }

    @Test
    @DisplayName("user_idあり → subクレームが含まれる")
    void withUser_subClaimIncluded() {
      Map<String, Object> body = new HashMap<>();
      body.put("client_id", "my-client");
      body.put("scopes", "openid");
      TokenCreateRequest request = new TokenCreateRequest(body);

      AuthorizationServerConfiguration serverConfig = createServerConfig(3600);
      LocalDateTime now = LocalDateTime.of(2026, 3, 19, 12, 0, 0);
      CreatedAt createdAt = new CreatedAt(now);
      ExpiresAt expiresAt = new ExpiresAt(now.plusSeconds(3600));

      User user = jsonConverter.read("{\"sub\": \"user-abc-123\"}", User.class);

      Map<String, Object> payload =
          factory.buildJwtPayload(
              request, serverConfig, "my-client", "openid", user, createdAt, expiresAt);

      assertEquals("user-abc-123", payload.get("sub"));
    }

    @Test
    @DisplayName("user_idなし → subクレームが含まれない")
    void noUser_noSubClaim() {
      Map<String, Object> body = new HashMap<>();
      body.put("client_id", "my-client");
      body.put("scopes", "openid");
      TokenCreateRequest request = new TokenCreateRequest(body);

      AuthorizationServerConfiguration serverConfig = createServerConfig(3600);
      LocalDateTime now = LocalDateTime.of(2026, 3, 19, 12, 0, 0);
      CreatedAt createdAt = new CreatedAt(now);
      ExpiresAt expiresAt = new ExpiresAt(now.plusSeconds(3600));

      Map<String, Object> payload =
          factory.buildJwtPayload(
              request, serverConfig, "my-client", "openid", new User(), createdAt, expiresAt);

      assertFalse(payload.containsKey("sub"));
    }

    @Test
    @DisplayName("必須標準クレームが全て含まれる")
    void requiredStandardClaims_allPresent() {
      Map<String, Object> body = new HashMap<>();
      body.put("client_id", "my-client");
      body.put("scopes", "openid");
      TokenCreateRequest request = new TokenCreateRequest(body);

      AuthorizationServerConfiguration serverConfig = createServerConfig(3600);
      LocalDateTime now = LocalDateTime.of(2026, 3, 19, 12, 0, 0);
      CreatedAt createdAt = new CreatedAt(now);
      ExpiresAt expiresAt = new ExpiresAt(now.plusSeconds(3600));

      Map<String, Object> payload =
          factory.buildJwtPayload(
              request, serverConfig, "my-client", "openid", new User(), createdAt, expiresAt);

      assertNotNull(payload.get("iss"));
      assertEquals("my-client", payload.get("client_id"));
      assertEquals("openid", payload.get("scope"));
      assertNotNull(payload.get("jti"));
      assertNotNull(payload.get("iat"));
      assertNotNull(payload.get("exp"));
    }

    @Test
    @DisplayName("custom_claimsなし → 標準クレームのみで正常生成")
    void noCustomClaims_onlyStandardClaims() {
      Map<String, Object> body = new HashMap<>();
      body.put("client_id", "my-client");
      body.put("scopes", "openid");
      TokenCreateRequest request = new TokenCreateRequest(body);

      AuthorizationServerConfiguration serverConfig = createServerConfig(3600);
      LocalDateTime now = LocalDateTime.of(2026, 3, 19, 12, 0, 0);
      CreatedAt createdAt = new CreatedAt(now);
      ExpiresAt expiresAt = new ExpiresAt(now.plusSeconds(3600));

      Map<String, Object> payload =
          factory.buildJwtPayload(
              request, serverConfig, "my-client", "openid", new User(), createdAt, expiresAt);

      assertNotNull(payload.get("iss"));
      assertNotNull(payload.get("jti"));
      assertEquals(6, payload.size()); // iss, client_id, scope, jti, iat, exp
    }

    @Test
    @DisplayName("custom_claimsが空Map → 標準クレームのみで正常生成")
    void emptyCustomClaims_onlyStandardClaims() {
      Map<String, Object> body = new HashMap<>();
      body.put("client_id", "my-client");
      body.put("scopes", "openid");
      body.put("custom_claims", new HashMap<>());
      TokenCreateRequest request = new TokenCreateRequest(body);

      AuthorizationServerConfiguration serverConfig = createServerConfig(3600);
      LocalDateTime now = LocalDateTime.of(2026, 3, 19, 12, 0, 0);
      CreatedAt createdAt = new CreatedAt(now);
      ExpiresAt expiresAt = new ExpiresAt(now.plusSeconds(3600));

      Map<String, Object> payload =
          factory.buildJwtPayload(
              request, serverConfig, "my-client", "openid", new User(), createdAt, expiresAt);

      assertEquals(6, payload.size());
    }

    @Test
    @DisplayName("iat/expの値がcreatedAt/expiresAtのエポック秒と一致する")
    void iatAndExp_matchEpochSeconds() {
      Map<String, Object> body = new HashMap<>();
      body.put("client_id", "my-client");
      body.put("scopes", "openid");
      TokenCreateRequest request = new TokenCreateRequest(body);

      AuthorizationServerConfiguration serverConfig = createServerConfig(3600);
      LocalDateTime now = LocalDateTime.of(2026, 3, 19, 12, 0, 0);
      CreatedAt createdAt = new CreatedAt(now);
      ExpiresAt expiresAt = new ExpiresAt(now.plusSeconds(3600));

      Map<String, Object> payload =
          factory.buildJwtPayload(
              request, serverConfig, "my-client", "openid", new User(), createdAt, expiresAt);

      assertEquals(createdAt.toEpochSecondWithUtc(), payload.get("iat"));
      assertEquals(expiresAt.toEpochSecondWithUtc(), payload.get("exp"));
    }

    @Test
    @DisplayName("scopeの値が渡したscopes文字列と一致する")
    void scope_matchesInputString() {
      Map<String, Object> body = new HashMap<>();
      body.put("client_id", "my-client");
      body.put("scopes", "openid profile email management");
      TokenCreateRequest request = new TokenCreateRequest(body);

      AuthorizationServerConfiguration serverConfig = createServerConfig(3600);
      LocalDateTime now = LocalDateTime.of(2026, 3, 19, 12, 0, 0);
      CreatedAt createdAt = new CreatedAt(now);
      ExpiresAt expiresAt = new ExpiresAt(now.plusSeconds(3600));

      Map<String, Object> payload =
          factory.buildJwtPayload(
              request,
              serverConfig,
              "my-client",
              "openid profile email management",
              new User(),
              createdAt,
              expiresAt);

      assertEquals("openid profile email management", payload.get("scope"));
    }

    @Test
    @DisplayName("jtiがUUID形式である")
    void jti_isValidUuid() {
      Map<String, Object> body = new HashMap<>();
      body.put("client_id", "my-client");
      body.put("scopes", "openid");
      TokenCreateRequest request = new TokenCreateRequest(body);

      AuthorizationServerConfiguration serverConfig = createServerConfig(3600);
      LocalDateTime now = LocalDateTime.of(2026, 3, 19, 12, 0, 0);
      CreatedAt createdAt = new CreatedAt(now);
      ExpiresAt expiresAt = new ExpiresAt(now.plusSeconds(3600));

      Map<String, Object> payload =
          factory.buildJwtPayload(
              request, serverConfig, "my-client", "openid", new User(), createdAt, expiresAt);

      String jti = (String) payload.get("jti");
      assertDoesNotThrow(() -> java.util.UUID.fromString(jti));
    }

    @Test
    @DisplayName("custom_claimsでsubを上書きできない（user_idあり）")
    void customClaimsSub_cannotOverwriteUserSub() {
      Map<String, Object> body = new HashMap<>();
      body.put("client_id", "my-client");
      body.put("scopes", "openid");
      body.put("custom_claims", Map.of("sub", "evil-user-id"));
      TokenCreateRequest request = new TokenCreateRequest(body);

      AuthorizationServerConfiguration serverConfig = createServerConfig(3600);
      LocalDateTime now = LocalDateTime.of(2026, 3, 19, 12, 0, 0);
      CreatedAt createdAt = new CreatedAt(now);
      ExpiresAt expiresAt = new ExpiresAt(now.plusSeconds(3600));

      User user = jsonConverter.read("{\"sub\": \"real-user-id\"}", User.class);

      Map<String, Object> payload =
          factory.buildJwtPayload(
              request, serverConfig, "my-client", "openid", user, createdAt, expiresAt);

      assertEquals("real-user-id", payload.get("sub"));
      assertNotEquals("evil-user-id", payload.get("sub"));
    }
  }

  @Nested
  @DisplayName("createRefreshToken")
  class CreateRefreshTokenTest {

    @Test
    @DisplayName("指定した有効期限でリフレッシュトークンが生成される")
    void createsWithCorrectExpiry() {
      LocalDateTime now = LocalDateTime.of(2026, 3, 19, 12, 0, 0);
      RefreshToken token = factory.createRefreshToken(86400, now);

      assertNotNull(token.refreshTokenEntity().value());
      assertFalse(token.refreshTokenEntity().value().isEmpty());
      assertEquals(now.plusSeconds(86400), token.expiresAt().value());
    }
  }

  @Nested
  @DisplayName("buildResponse")
  class BuildResponseTest {

    @Test
    @DisplayName("refresh_tokenがnullの場合はレスポンスに含まれない")
    void noRefreshToken_notInResponse() {
      Map<String, Object> response =
          factory.buildResponse("id-1", "at-value", "Bearer", 3600, null, "openid");

      assertEquals("id-1", response.get("id"));
      assertEquals("at-value", response.get("access_token"));
      assertEquals("Bearer", response.get("token_type"));
      assertEquals(3600L, response.get("expires_in"));
      assertFalse(response.containsKey("refresh_token"));
      assertEquals("openid", response.get("scopes"));
    }

    @Test
    @DisplayName("refresh_tokenがある場合はレスポンスに含まれる")
    void withRefreshToken_inResponse() {
      Map<String, Object> response =
          factory.buildResponse("id-1", "at-value", "Bearer", 3600, "rt-value", "openid");

      assertEquals("rt-value", response.get("refresh_token"));
    }
  }

  // Helper methods

  private AuthorizationServerConfiguration createServerConfig(long accessTokenDuration) {
    Map<String, Object> config = new HashMap<>();
    config.put("token_endpoint", "https://example.com/token");
    config.put("issuer", "https://example.com");
    config.put(
        "extension",
        Map.of(
            "access_token_type",
            "JWT",
            "access_token_duration",
            accessTokenDuration,
            "refresh_token_duration",
            86400));
    return jsonConverter.read(jsonConverter.write(config), AuthorizationServerConfiguration.class);
  }

  private AuthorizationServerConfiguration createServerConfig(boolean isIdentifier) {
    Map<String, Object> config = new HashMap<>();
    config.put("token_endpoint", "https://example.com/token");
    config.put("issuer", "https://example.com");
    config.put(
        "extension",
        Map.of(
            "access_token_type", isIdentifier ? "opaque" : "JWT",
            "access_token_duration", 3600,
            "refresh_token_duration", 86400));
    return jsonConverter.read(jsonConverter.write(config), AuthorizationServerConfiguration.class);
  }

  private ClientConfiguration createClientConfig(long accessTokenDuration) {
    Map<String, Object> config = new HashMap<>();
    config.put("client_id", "test-client");
    config.put("client_name", "Test Client");
    if (accessTokenDuration > 0) {
      config.put("extension", Map.of("access_token_duration", accessTokenDuration));
    }
    return jsonConverter.read(jsonConverter.write(config), ClientConfiguration.class);
  }
}
