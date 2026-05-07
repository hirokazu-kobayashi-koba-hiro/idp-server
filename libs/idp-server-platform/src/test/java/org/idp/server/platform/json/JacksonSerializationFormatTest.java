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

package org.idp.server.platform.json;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Jackson のシリアライズ出力形式を記録するテスト。
 *
 * <p>Jackson 2 → 3 移行時にシリアライズ形式が変わると、Jackson 2 で書いたデータを Jackson 3 で読めない（またはその
 * 逆）という互換性問題が発生する。このテストは現在の Jackson バージョンの出力形式を記録し、移行時に変化を検出する。
 *
 * <p>Blue-Green デプロイやロールバック時に、新旧バージョンのアプリが同じデータストア（Redis, DB）を 参照するため、JSON 形式の互換性は必須。
 */
class JacksonSerializationFormatTest {

  private final JsonConverter defaultConverter = JsonConverter.defaultInstance();
  private final JsonConverter snakeCaseConverter = JsonConverter.snakeCaseInstance();

  @Nested
  class InstantFormat {

    @Test
    void serializesInstantAsEpochSeconds() {
      // Jackson 2 + JavaTimeModule: Instant → epoch seconds (e.g. 1776506400.000000000)
      InstantHolder obj = new InstantHolder();
      obj.timestamp = Instant.parse("2026-04-18T10:00:00Z");

      String json = defaultConverter.write(obj);

      assertTrue(
          json.contains("1776506400"), "Instant should be serialized as epoch seconds: " + json);
      assertFalse(
          json.contains("2026-04-18"),
          "Instant should NOT be serialized as ISO-8601 string: " + json);

      InstantHolder restored = defaultConverter.read(json, InstantHolder.class);
      assertEquals(obj.timestamp, restored.timestamp);
    }

    @Test
    void serializesInstantAsEpochSecondsWithSnakeCase() {
      InstantHolder obj = new InstantHolder();
      obj.timestamp = Instant.parse("2026-04-18T10:00:00Z");

      String json = snakeCaseConverter.write(obj);

      assertTrue(
          json.contains("1776506400"), "Instant should be serialized as epoch seconds: " + json);

      InstantHolder restored = snakeCaseConverter.read(json, InstantHolder.class);
      assertEquals(obj.timestamp, restored.timestamp);
    }

    @Test
    void canDeserializeEpochSecondsFormat() {
      // 数値（epoch seconds）形式で書かれた Instant を読めるか
      String json = "{\"timestamp\":1776178800.000000000}";

      InstantHolder result = defaultConverter.read(json, InstantHolder.class);
      assertNotNull(result.timestamp);
    }

    @Test
    void canDeserializeEpochSecondsAsLong() {
      // 整数の epoch seconds
      String json = "{\"timestamp\":1776178800}";

      InstantHolder result = defaultConverter.read(json, InstantHolder.class);
      assertNotNull(result.timestamp);
    }

    @Test
    void canDeserializeIso8601StringFormat() {
      // Jackson 2 + JavaTimeModule は ISO-8601 文字列形式の Instant を読める
      // → Jackson 3 が ISO-8601 で出力しても、ロールバック時に Jackson 2 で読める
      String json = "{\"timestamp\":\"2026-04-18T10:00:00Z\"}";

      InstantHolder result =
          assertDoesNotThrow(() -> defaultConverter.read(json, InstantHolder.class));
      assertEquals(Instant.parse("2026-04-18T10:00:00Z"), result.timestamp);
    }
  }

  @Nested
  class LocalDateTimeFormat {

    @Test
    void serializesLocalDateTimeAsArray() {
      // Jackson 2 + JavaTimeModule (WRITE_DATES_AS_TIMESTAMPS=true): LocalDateTime → 配列形式
      LocalDateTimeHolder obj = new LocalDateTimeHolder();
      obj.timestamp = LocalDateTime.of(2026, 4, 18, 10, 30, 0);

      String json = defaultConverter.write(obj);

      assertTrue(
          json.contains("[2026,4,18,10,30]"),
          "LocalDateTime should be serialized as array: " + json);
      assertFalse(
          json.contains("2026-04-18"),
          "LocalDateTime should NOT be serialized as ISO-8601 string: " + json);

      LocalDateTimeHolder restored = defaultConverter.read(json, LocalDateTimeHolder.class);
      assertEquals(obj.timestamp, restored.timestamp);
    }

    @Test
    void canDeserializeArrayFormat() {
      // Jackson 2 のデフォルト array 形式
      String json = "{\"timestamp\":[2026,4,18,10,30,0]}";

      LocalDateTimeHolder result = defaultConverter.read(json, LocalDateTimeHolder.class);
      assertEquals(LocalDateTime.of(2026, 4, 18, 10, 30, 0), result.timestamp);
    }

    @Test
    void canDeserializeCustomStringFormat() {
      // JsonConverter に登録済みのカスタム形式
      String json = "{\"timestamp\":\"2026/04/18 10:30:00\"}";

      LocalDateTimeHolder result = defaultConverter.read(json, LocalDateTimeHolder.class);
      assertEquals(LocalDateTime.of(2026, 4, 18, 10, 30, 0), result.timestamp);
    }

    @Test
    void cannotDeserializeIso8601StringFormat() {
      // Jackson 2 は ISO-8601 文字列形式の LocalDateTime を読めない
      // Jackson 3 がこの形式で出力する場合、ロールバック時に問題になる
      // → Jackson 2 側に ISO-8601 デシリアライザを追加して対応が必要
      String json = "{\"timestamp\":\"2026-04-18T10:30:00\"}";

      assertThrows(
          JsonRuntimeException.class,
          () -> defaultConverter.read(json, LocalDateTimeHolder.class),
          "Jackson 2 cannot deserialize ISO-8601 string for LocalDateTime. "
              + "This is a known limitation that must be fixed before Jackson 3 migration.");
    }
  }

  @Nested
  class EnumFormat {

    @Test
    void recordsEnumSerializationFormat() {
      EnumHolder obj = new EnumHolder();
      obj.status = Status.ACTIVE;

      String json = defaultConverter.write(obj);

      // enum 名（"ACTIVE"）で出力されること
      assertTrue(json.contains("\"ACTIVE\""), "Enum should serialize as name string: " + json);

      EnumHolder restored = defaultConverter.read(json, EnumHolder.class);
      assertEquals(Status.ACTIVE, restored.status);
    }
  }

  @Nested
  class ComplexObjectFormat {

    @Test
    void recordsSessionLikeObjectFormat() {
      // OPSession のような複合オブジェクトのシリアライズ形式を記録
      SessionLikeObject obj = new SessionLikeObject();
      obj.id = "ops_test-123";
      obj.tenantId = "tenant-001";
      obj.authTime = Instant.parse("2026-04-18T10:00:00Z");
      obj.createdAt = Instant.parse("2026-04-18T10:00:00Z");
      obj.expiresAt = Instant.parse("2026-04-18T11:00:00Z");
      obj.status = Status.ACTIVE;
      obj.amr = List.of("pwd", "otp");
      obj.metadata = Map.of("key", "value");

      String json = snakeCaseConverter.write(obj);

      // Instant が epoch seconds 形式であること
      assertTrue(json.contains("1776506400"), "auth_time should be epoch seconds: " + json);
      // snake_case であること
      assertTrue(json.contains("\"tenant_id\""), "should use snake_case: " + json);
      assertTrue(json.contains("\"auth_time\""), "should use snake_case: " + json);

      // ラウンドトリップ
      SessionLikeObject restored = snakeCaseConverter.read(json, SessionLikeObject.class);
      assertEquals(obj.id, restored.id);
      assertEquals(obj.tenantId, restored.tenantId);
      assertEquals(obj.authTime, restored.authTime);
      assertEquals(obj.createdAt, restored.createdAt);
      assertEquals(obj.expiresAt, restored.expiresAt);
      assertEquals(obj.status, restored.status);
      assertEquals(obj.amr, restored.amr);
      assertEquals("value", restored.metadata.get("key"));
    }

    @Test
    void canDeserializeWithMissingFields() {
      // 旧バージョンで書かれた JSON にフィールドが足りない場合
      String json = "{\"id\":\"ops_test\",\"status\":\"ACTIVE\"}";

      SessionLikeObject result = snakeCaseConverter.read(json, SessionLikeObject.class);

      assertEquals("ops_test", result.id);
      assertEquals(Status.ACTIVE, result.status);
      assertNull(result.authTime);
      assertNull(result.amr);
    }

    @Test
    void rejectsUnknownFieldsByDefault() {
      // JsonConverter のデフォルト: FAIL_ON_UNKNOWN_PROPERTIES=true
      // JsonReadable を実装しないクラスは未知フィールドでエラーになる
      // → 設定 JSON や API リクエストの typo を検出できる
      String json =
          "{\"id\":\"ops_test\",\"status\":\"ACTIVE\","
              + "\"unknown_field\":\"should_be_rejected\","
              + "\"another_new_field\":123}";

      assertThrows(
          JsonRuntimeException.class,
          () -> snakeCaseConverter.read(json, SessionLikeObject.class),
          "Classes without JsonReadable should reject unknown fields");
    }

    @Test
    void ignoresUnknownFieldsWithJsonReadable() {
      // JsonReadable を実装したクラスは未知フィールドを無視する
      // → Jackson 3 移行時のロールバック安全性を確保
      String json =
          "{\"id\":\"ops_test\",\"status\":\"ACTIVE\","
              + "\"unknown_field\":\"should_be_ignored\","
              + "\"another_new_field\":123}";

      JsonReadableSessionLikeObject result =
          assertDoesNotThrow(
              () -> snakeCaseConverter.read(json, JsonReadableSessionLikeObject.class),
              "Classes with JsonReadable should ignore unknown fields");

      assertEquals("ops_test", result.id);
      assertEquals(Status.ACTIVE, result.status);
    }
  }

  // --- test helper classes ---

  static class InstantHolder {
    Instant timestamp;

    InstantHolder() {}
  }

  static class LocalDateTimeHolder {
    LocalDateTime timestamp;

    LocalDateTimeHolder() {}
  }

  enum Status {
    ACTIVE,
    EXPIRED,
    TERMINATED
  }

  static class EnumHolder {
    Status status;

    EnumHolder() {}
  }

  static class SessionLikeObject {
    String id;
    String tenantId;
    Instant authTime;
    Instant createdAt;
    Instant expiresAt;
    Status status;
    List<String> amr;
    Map<String, String> metadata;

    SessionLikeObject() {}
  }

  static class JsonReadableSessionLikeObject implements JsonReadable {
    String id;
    Status status;

    JsonReadableSessionLikeObject() {}
  }
}
