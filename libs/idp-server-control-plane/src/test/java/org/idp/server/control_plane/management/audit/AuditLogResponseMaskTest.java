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

package org.idp.server.control_plane.management.audit;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditLogResponseMaskTest {

  AuditLogResponseMask mask = new AuditLogResponseMask();

  @Test
  @DisplayName("秘密の値を、入れ子のオブジェクトと配列の中まで隠す")
  void masksNestedSecrets() {
    Map<String, Object> log = new HashMap<>();
    log.put("type", "client_create");
    log.put(
        "request",
        Map.of(
            "client_id",
            "client-1",
            "client_secret",
            "s3cret",
            "extension",
            Map.of("access_token_duration", 3600, "api_key", "k-1")));
    log.put(
        "after",
        Map.of(
            "interactions",
            List.of(
                Map.of("raw_password", "p@ss"),
                Map.of("http_request", Map.of("access_token", "at")))));

    Map<String, Object> masked = mask.apply(log);

    assertEquals("client_create", masked.get("type"));
    Map<?, ?> request = (Map<?, ?>) masked.get("request");
    assertEquals("client-1", request.get("client_id"));
    assertEquals("[SCRUBBED]", request.get("client_secret"));
    Map<?, ?> extension = (Map<?, ?>) request.get("extension");
    assertEquals(3600, extension.get("access_token_duration"));
    assertEquals("[SCRUBBED]", extension.get("api_key"));
    List<?> interactions = (List<?>) ((Map<?, ?>) masked.get("after")).get("interactions");
    assertEquals("[SCRUBBED]", ((Map<?, ?>) interactions.get(0)).get("raw_password"));
    Map<?, ?> httpRequest = (Map<?, ?>) ((Map<?, ?>) interactions.get(1)).get("http_request");
    assertEquals("[SCRUBBED]", httpRequest.get("access_token"));
  }

  @Test
  @DisplayName("キーは丸ごと一致で比べる。秘密のことを書いているだけの項目は隠さない")
  void matchesWholeKeysOnly() {
    Map<String, Object> masked =
        mask.apply(
            Map.of(
                "client_secret_expires_at", 0,
                "token_endpoint", "https://example.com/token",
                "token_endpoint_auth_method", "client_secret_basic",
                "jwks_uri", "https://example.com/jwks",
                "password_policy", Map.of("min_length", 8)));

    assertEquals(0, masked.get("client_secret_expires_at"));
    assertEquals("https://example.com/token", masked.get("token_endpoint"));
    assertEquals("client_secret_basic", masked.get("token_endpoint_auth_method"));
    assertEquals("https://example.com/jwks", masked.get("jwks_uri"));
    assertEquals(Map.of("min_length", 8), masked.get("password_policy"));
  }

  @Test
  @DisplayName("大文字小文字を区別しない（HTTP ヘッダーの Authorization など）")
  void ignoresCase() {
    Map<String, Object> masked =
        mask.apply(Map.of("headers", Map.of("Authorization", "Bearer x", "Cookie", "a=b")));

    Map<?, ?> headers = (Map<?, ?>) masked.get("headers");
    assertEquals("[SCRUBBED]", headers.get("Authorization"));
    assertEquals("[SCRUBBED]", headers.get("Cookie"));
  }

  @Test
  @DisplayName("資格情報を運ぶヘッダー名がキーになっているときも隠す（X-API-Key など）")
  void masksCredentialHeadersAsKeys() {
    Map<String, Object> masked =
        mask.apply(
            Map.of(
                "headers",
                Map.of(
                    "X-API-Key",
                    "k-1",
                    "X-Auth-Token",
                    "t-1",
                    "Content-Type",
                    "application/json")));

    Map<?, ?> headers = (Map<?, ?>) masked.get("headers");
    assertEquals("[SCRUBBED]", headers.get("X-API-Key"));
    assertEquals("[SCRUBBED]", headers.get("X-Auth-Token"));
    assertEquals("application/json", headers.get("Content-Type"));
  }

  @Test
  @DisplayName("値がオブジェクトでも、キーが当たれば丸ごと隠す（認可サーバーの jwks など）")
  void masksWholeValueOfMatchedKey() {
    Map<String, Object> masked =
        mask.apply(Map.of("jwks", Map.of("keys", List.of(Map.of("kty", "EC", "d", "private")))));

    assertEquals("[SCRUBBED]", masked.get("jwks"));
  }

  @Test
  @DisplayName("元の値は変えない")
  void leavesTheOriginalUntouched() {
    Map<String, Object> original = new HashMap<>();
    original.put("client_secret", "s3cret");

    mask.apply(original);

    assertEquals("s3cret", original.get("client_secret"));
    assertNull(mask.apply(null));
  }

  @Test
  @DisplayName("マッピングルールの static_value は、送り先が秘密の項目やヘッダーなら隠す")
  void masksStaticValueSentToACredential() {
    Map<String, Object> masked =
        mask.apply(
            Map.of(
                "header_mapping_rules",
                List.of(
                    Map.of("static_value", "application/json", "to", "Content-Type"),
                    Map.of("static_value", "Bearer s3cret", "to", "Authorization"),
                    Map.of("static_value", "k-1", "to", "X-API-Key")),
                "body_mapping_rules",
                List.of(
                    Map.of("static_value", "s3cret", "to", "client_secret"),
                    Map.of("static_value", "s3cret", "to", "credentials.client_secret"),
                    Map.of("static_value", "client_credentials", "to", "grant_type"),
                    Map.of("from", "$.request_body.username", "to", "username"))));

    List<?> headers = (List<?>) masked.get("header_mapping_rules");
    assertEquals("application/json", ((Map<?, ?>) headers.get(0)).get("static_value"));
    assertEquals("[SCRUBBED]", ((Map<?, ?>) headers.get(1)).get("static_value"));
    assertEquals("Authorization", ((Map<?, ?>) headers.get(1)).get("to"));
    assertEquals("[SCRUBBED]", ((Map<?, ?>) headers.get(2)).get("static_value"));
    List<?> body = (List<?>) masked.get("body_mapping_rules");
    assertEquals("[SCRUBBED]", ((Map<?, ?>) body.get(0)).get("static_value"));
    assertEquals("[SCRUBBED]", ((Map<?, ?>) body.get(1)).get("static_value"));
    assertEquals("client_credentials", ((Map<?, ?>) body.get(2)).get("static_value"));
    assertEquals("$.request_body.username", ((Map<?, ?>) body.get(3)).get("from"));
  }

  @Test
  @DisplayName("それだけで資格情報になる値（Slack の Webhook URL、端末の通知トークン）も隠す")
  void masksValuesThatAreCredentialsOnTheirOwn() {
    Map<String, Object> masked =
        mask.apply(
            Map.of(
                "incoming_webhook_url",
                "https://hooks.slack.com/services/x",
                "authentication_devices",
                List.of(Map.of("id", "d-1", "notification_token", "t"))));

    assertEquals("[SCRUBBED]", masked.get("incoming_webhook_url"));
    Map<?, ?> device = (Map<?, ?>) ((List<?>) masked.get("authentication_devices")).get(0);
    assertEquals("d-1", device.get("id"));
    assertEquals("[SCRUBBED]", device.get("notification_token"));
  }
}
