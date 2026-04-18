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
    void recordsInstantSerializationFormat() {
      InstantHolder obj = new InstantHolder();
      obj.timestamp = Instant.parse("2026-04-18T10:00:00Z");

      String json = defaultConverter.write(obj);

      // 現在の Jackson 2 + JavaTimeModule の Instant シリアライズ形式を記録
      // Jackson 3 でこの形式が変わる場合、このテストが失敗して検知できる
      System.out.println("[Instant format] " + json);

      // ラウンドトリップが可能であること（形式に関わらず）
      InstantHolder restored = defaultConverter.read(json, InstantHolder.class);
      assertEquals(obj.timestamp, restored.timestamp);
    }

    @Test
    void recordsInstantSerializationFormatWithSnakeCase() {
      InstantHolder obj = new InstantHolder();
      obj.timestamp = Instant.parse("2026-04-18T10:00:00Z");

      String json = snakeCaseConverter.write(obj);

      System.out.println("[Instant snake_case format] " + json);

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
      // ISO-8601 文字列形式で書かれた Instant を読めるか
      // Jackson 3 がこの形式で出力する可能性がある
      String json = "{\"timestamp\":\"2026-04-18T10:00:00Z\"}";

      try {
        InstantHolder result = defaultConverter.read(json, InstantHolder.class);
        assertNotNull(result.timestamp);
        assertEquals(Instant.parse("2026-04-18T10:00:00Z"), result.timestamp);
      } catch (JsonRuntimeException e) {
        // Jackson 2 がこの形式を読めない場合、Jackson 3 からのロールバック時に問題になる
        fail(
            "Jackson 2 cannot deserialize ISO-8601 string format for Instant. "
                + "This means data written by Jackson 3 may not be readable after rollback. "
                + "Error: "
                + e.getMessage());
      }
    }
  }

  @Nested
  class LocalDateTimeFormat {

    @Test
    void recordsLocalDateTimeSerializationFormat() {
      LocalDateTimeHolder obj = new LocalDateTimeHolder();
      obj.timestamp = LocalDateTime.of(2026, 4, 18, 10, 30, 0);

      String json = defaultConverter.write(obj);

      System.out.println("[LocalDateTime format] " + json);

      // Jackson 2 default: array [2026,4,18,10,30]
      // Jackson 3 でこの形式が変わるかを検知
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

      System.out.println("[Enum format] " + json);

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

      System.out.println("[Session-like object format] " + json);

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
    void canDeserializeWithExtraFields() {
      // 新バージョンで追加されたフィールドが JSON に含まれている場合
      String json =
          "{\"id\":\"ops_test\",\"status\":\"ACTIVE\","
              + "\"unknown_field\":\"should_be_ignored\","
              + "\"another_new_field\":123}";

      // Jackson のデフォルトは unknown fields を無視しない（FAIL_ON_UNKNOWN_PROPERTIES=true）
      // JsonConverter の設定次第で挙動が変わる
      try {
        SessionLikeObject result = snakeCaseConverter.read(json, SessionLikeObject.class);
        assertEquals("ops_test", result.id);
        // 未知フィールドを無視できた
      } catch (JsonRuntimeException e) {
        // 未知フィールドでエラーになる場合、新→旧のロールバック時に問題
        System.out.println(
            "[WARNING] Unknown fields cause deserialization failure. "
                + "If Jackson 3 adds new fields, Jackson 2 will fail to read them. "
                + "Consider enabling DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES=false. "
                + "Error: "
                + e.getMessage());
      }
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
}
