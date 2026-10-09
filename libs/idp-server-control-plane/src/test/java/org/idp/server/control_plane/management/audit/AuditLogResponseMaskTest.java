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
}
