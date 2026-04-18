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

class JsonConverterTest {

  @Nested
  class ReadWriteRoundTrip {

    @Test
    void serializesAndDeserializesSimpleObject() {
      JsonConverter converter = JsonConverter.defaultInstance();
      SampleObject original = new SampleObject("hello", 42, true);

      String json = converter.write(original);
      SampleObject restored = converter.read(json, SampleObject.class);

      assertEquals("hello", restored.name);
      assertEquals(42, restored.count);
      assertTrue(restored.active);
    }

    @Test
    void serializesPrivateFieldsWithoutGetters() {
      JsonConverter converter = JsonConverter.defaultInstance();
      PrivateFieldObject original = new PrivateFieldObject("secret", 99);

      String json = converter.write(original);
      PrivateFieldObject restored = converter.read(json, PrivateFieldObject.class);

      assertEquals("secret", restored.getValue());
      assertEquals(99, restored.getNumber());
    }

    @Test
    void serializesNestedObjects() {
      JsonConverter converter = JsonConverter.defaultInstance();
      NestedObject original = new NestedObject("parent", new SampleObject("child", 1, false));

      String json = converter.write(original);
      NestedObject restored = converter.read(json, NestedObject.class);

      assertEquals("parent", restored.label);
      assertEquals("child", restored.inner.name);
      assertEquals(1, restored.inner.count);
      assertFalse(restored.inner.active);
    }

    @Test
    void serializesCollections() {
      JsonConverter converter = JsonConverter.defaultInstance();
      CollectionObject original =
          new CollectionObject(List.of("a", "b", "c"), Map.of("key1", "val1"));

      String json = converter.write(original);
      CollectionObject restored = converter.read(json, CollectionObject.class);

      assertEquals(List.of("a", "b", "c"), restored.items);
      assertEquals("val1", restored.metadata.get("key1"));
    }

    @Test
    void handlesNullFields() {
      JsonConverter converter = JsonConverter.defaultInstance();
      SampleObject original = new SampleObject(null, 0, false);

      String json = converter.write(original);
      SampleObject restored = converter.read(json, SampleObject.class);

      assertNull(restored.name);
      assertEquals(0, restored.count);
      assertFalse(restored.active);
    }
  }

  @Nested
  class SnakeCaseStrategy {

    @Test
    void convertsFieldNamesToSnakeCase() {
      JsonConverter converter = JsonConverter.snakeCaseInstance();
      CamelCaseObject original = new CamelCaseObject("test", 100);

      String json = converter.write(original);

      assertTrue(json.contains("\"first_name\""), "should use snake_case: " + json);
      assertTrue(json.contains("\"max_retry_count\""), "should use snake_case: " + json);
      assertFalse(json.contains("\"firstName\""), "should not contain camelCase: " + json);
    }

    @Test
    void roundTripsWithSnakeCase() {
      JsonConverter converter = JsonConverter.snakeCaseInstance();
      CamelCaseObject original = new CamelCaseObject("test", 100);

      String json = converter.write(original);
      CamelCaseObject restored = converter.read(json, CamelCaseObject.class);

      assertEquals("test", restored.firstName);
      assertEquals(100, restored.maxRetryCount);
    }
  }

  @Nested
  class CoercionBehavior {

    @Test
    void coercesEmptyStringToNullForCollections() {
      JsonConverter converter = JsonConverter.defaultInstance();
      String json = "{\"values\":\"\"}";

      ListOnlyObject result = converter.read(json, ListOnlyObject.class);

      assertNull(result.values, "empty string should be coerced to null for collection fields");
    }

    @Test
    void rejectsNonEmptyStringForCollections() {
      JsonConverter converter = JsonConverter.defaultInstance();
      String json = "{\"values\":\"not-a-list\"}";

      assertThrows(
          JsonRuntimeException.class,
          () -> converter.read(json, ListOnlyObject.class),
          "non-empty string for collection field should throw");
    }
  }

  @Nested
  class LocalDateTimeHandling {

    @Test
    void deserializesCustomDateTimeFormat() {
      JsonConverter converter = JsonConverter.defaultInstance();
      String json = "{\"timestamp\":\"2026/04/18 10:30:00\"}";

      DateTimeObject result = converter.read(json, DateTimeObject.class);

      assertEquals(LocalDateTime.of(2026, 4, 18, 10, 30, 0), result.timestamp);
    }

    @Test
    void serializesLocalDateTimeAsArray() {
      JsonConverter converter = JsonConverter.defaultInstance();
      DateTimeObject obj = new DateTimeObject();
      obj.timestamp = LocalDateTime.of(2026, 4, 18, 10, 30, 0);

      String json = converter.write(obj);

      // Jackson default: LocalDateTime → array [year, month, day, hour, minute, second]
      assertTrue(json.contains("[2026,4,18,10,30]"), "should serialize as array: " + json);
    }

    @Test
    void roundTripsLocalDateTime() {
      JsonConverter converter = JsonConverter.defaultInstance();
      DateTimeObject original = new DateTimeObject();
      original.timestamp = LocalDateTime.of(2026, 4, 18, 10, 30, 0);

      String json = converter.write(original);
      DateTimeObject restored = converter.read(json, DateTimeObject.class);

      assertEquals(original.timestamp, restored.timestamp);
    }

    @Test
    void deserializesArrayFormat() {
      JsonConverter converter = JsonConverter.defaultInstance();
      // Jackson 2 のデフォルト配列形式
      String json = "{\"timestamp\":[2026,4,18,10,30,0]}";

      DateTimeObject result = converter.read(json, DateTimeObject.class);

      assertEquals(LocalDateTime.of(2026, 4, 18, 10, 30, 0), result.timestamp);
    }

    @Test
    void handlesLocalDateTimeWithSeconds() {
      JsonConverter converter = JsonConverter.defaultInstance();
      String json = "{\"timestamp\":\"2026/04/18 10:30:45\"}";

      DateTimeObject result = converter.read(json, DateTimeObject.class);

      assertEquals(LocalDateTime.of(2026, 4, 18, 10, 30, 45), result.timestamp);
    }

    @Test
    void handlesNullLocalDateTime() {
      JsonConverter converter = JsonConverter.defaultInstance();
      String json = "{\"timestamp\":null}";

      DateTimeObject result = converter.read(json, DateTimeObject.class);

      assertNull(result.timestamp);
    }
  }

  @Nested
  class ReadTree {

    @Test
    void readsJsonStringToJsonNodeWrapper() {
      JsonConverter converter = JsonConverter.defaultInstance();
      String json = "{\"name\":\"test\",\"count\":42}";

      JsonNodeWrapper wrapper = converter.readTree(json);

      assertEquals("test", wrapper.getValueOrEmptyAsString("name"));
      assertEquals(42, wrapper.getValueAsInt("count"));
    }

    @Test
    void readsMapToJsonNodeWrapper() {
      JsonConverter converter = JsonConverter.defaultInstance();
      Map<String, Object> map = Map.of("key", "value", "number", 123);

      JsonNodeWrapper wrapper = converter.readTree(map);

      assertEquals("value", wrapper.getValueOrEmptyAsString("key"));
      assertEquals(123, wrapper.getValueAsInt("number"));
    }

    @Test
    void readsObjectToJsonNodeWrapper() {
      JsonConverter converter = JsonConverter.defaultInstance();
      SampleObject obj = new SampleObject("test", 42, true);

      JsonNodeWrapper wrapper = converter.readTree(obj);

      assertTrue(wrapper.exists());
      assertTrue(wrapper.contains("name"));
    }
  }

  @Nested
  class ConvertValue {

    @Test
    void convertsMapToObject() {
      JsonConverter converter = JsonConverter.defaultInstance();
      Map<String, Object> map = Map.of("name", "fromMap", "count", 7, "active", true);

      SampleObject result = converter.read(map, SampleObject.class);

      assertEquals("fromMap", result.name);
      assertEquals(7, result.count);
      assertTrue(result.active);
    }
  }

  @Nested
  class PrivateFinalFieldHandling {

    @Test
    void deserializesPrivateNonFinalFields() {
      JsonConverter converter = JsonConverter.defaultInstance();
      String json = "{\"value\":\"hello\",\"number\":42}";

      PrivateFieldObject result = converter.read(json, PrivateFieldObject.class);

      assertEquals("hello", result.getValue());
      assertEquals(42, result.getNumber());
    }

    @Test
    void deserializesPrivateFinalFields() {
      // Jackson 2: private final フィールドへのリフレクション書き込みが可能
      // Jackson 3: private final フィールドへの書き込みは不可（テスト失敗する想定）
      // PR #1424 では final を外すことで対応
      JsonConverter converter = JsonConverter.defaultInstance();
      String json = "{\"code\":\"ABC\",\"amount\":100}";

      PrivateFinalFieldObject result = converter.read(json, PrivateFinalFieldObject.class);

      assertEquals("ABC", result.getCode());
      assertEquals(100, result.getAmount());
    }

    @Test
    void roundTripsPrivateFinalFields() {
      JsonConverter converter = JsonConverter.defaultInstance();
      PrivateFinalFieldObject original = new PrivateFinalFieldObject("XYZ", 999);

      String json = converter.write(original);
      PrivateFinalFieldObject restored = converter.read(json, PrivateFinalFieldObject.class);

      assertEquals(original.getCode(), restored.getCode());
      assertEquals(original.getAmount(), restored.getAmount());
    }

    @Test
    void deserializesPrivateFinalBooleanField() {
      // boolean/Boolean の final フィールドも Jackson 3 で問題になる
      JsonConverter converter = JsonConverter.defaultInstance();
      String json = "{\"enabled\":true,\"label\":\"test\"}";

      PrivateFinalBooleanObject result = converter.read(json, PrivateFinalBooleanObject.class);

      assertTrue(result.isEnabled());
      assertEquals("test", result.getLabel());
    }
  }

  @Nested
  class ValueObjectWrapping {

    @Test
    void roundTripsSingleFieldValueObject() {
      // OPSessionIdentifier, TenantIdentifier 等のパターン
      JsonConverter converter = JsonConverter.defaultInstance();
      SingleValueObject original = new SingleValueObject("ops_test-id-123");

      String json = converter.write(original);
      SingleValueObject restored = converter.read(json, SingleValueObject.class);

      assertEquals("ops_test-id-123", restored.value);
    }

    @Test
    void roundTripsNestedValueObjects() {
      // OPSession が OPSessionIdentifier, TenantIdentifier を含むパターン
      JsonConverter converter = JsonConverter.snakeCaseInstance();
      ValueObjectContainer original =
          new ValueObjectContainer(
              new SingleValueObject("session-1"), new SingleValueObject("tenant-1"), "ACTIVE");

      String json = converter.write(original);
      ValueObjectContainer restored = converter.read(json, ValueObjectContainer.class);

      assertEquals("session-1", restored.sessionId.value);
      assertEquals("tenant-1", restored.tenantId.value);
      assertEquals("ACTIVE", restored.status);
    }

    @Test
    void jsonContainsNestedValueStructure() {
      JsonConverter converter = JsonConverter.snakeCaseInstance();
      ValueObjectContainer original =
          new ValueObjectContainer(
              new SingleValueObject("s1"), new SingleValueObject("t1"), "ACTIVE");

      String json = converter.write(original);

      // 値オブジェクトはネストされた {"value": "..."} として出力される
      assertTrue(json.contains("\"session_id\""), "should contain session_id: " + json);
      assertTrue(json.contains("\"tenant_id\""), "should contain tenant_id: " + json);
    }
  }

  @Nested
  class InstantHandling {

    @Test
    void serializesInstant() {
      JsonConverter converter = JsonConverter.defaultInstance();
      InstantObject obj = new InstantObject();
      obj.createdAt = Instant.parse("2026-04-18T10:00:00Z");

      String json = converter.write(obj);

      // Instant がシリアライズされること（具体的な形式はJacksonバージョンに依存）
      assertNotNull(json);
      assertFalse(json.contains("\"createdAt\":null"), "should not be null: " + json);
    }

    @Test
    void roundTripsInstant() {
      JsonConverter converter = JsonConverter.defaultInstance();
      InstantObject original = new InstantObject();
      original.createdAt = Instant.parse("2026-04-18T10:00:00Z");

      String json = converter.write(original);
      InstantObject restored = converter.read(json, InstantObject.class);

      assertEquals(original.createdAt, restored.createdAt);
    }

    @Test
    void handlesNullInstant() {
      JsonConverter converter = JsonConverter.defaultInstance();
      String json = "{\"createdAt\":null}";

      InstantObject result = converter.read(json, InstantObject.class);

      assertNull(result.createdAt);
    }

    @Test
    void roundTripsInstantWithSnakeCase() {
      JsonConverter converter = JsonConverter.snakeCaseInstance();
      InstantSnakeCaseObject original = new InstantSnakeCaseObject();
      original.createdAt = Instant.parse("2026-04-18T10:00:00Z");
      original.expiresAt = Instant.parse("2026-04-18T11:00:00Z");

      String json = converter.write(original);
      InstantSnakeCaseObject restored = converter.read(json, InstantSnakeCaseObject.class);

      assertEquals(original.createdAt, restored.createdAt);
      assertEquals(original.expiresAt, restored.expiresAt);
      assertTrue(json.contains("\"created_at\""), "should use snake_case: " + json);
      assertTrue(json.contains("\"expires_at\""), "should use snake_case: " + json);
    }
  }

  @Nested
  class BooleanHandling {

    @Test
    void deserializesPrimitiveBoolean() {
      JsonConverter converter = JsonConverter.defaultInstance();
      String json = "{\"name\":\"test\",\"count\":0,\"active\":true}";

      SampleObject result = converter.read(json, SampleObject.class);

      assertTrue(result.active);
    }

    @Test
    void deserializesBooleanObjectField() {
      JsonConverter converter = JsonConverter.defaultInstance();
      String json = "{\"enabled\":true}";

      BooleanObjectField result = converter.read(json, BooleanObjectField.class);

      assertTrue(result.enabled);
    }

    @Test
    void deserializesNullBooleanObjectField() {
      JsonConverter converter = JsonConverter.defaultInstance();
      String json = "{\"enabled\":null}";

      BooleanObjectField result = converter.read(json, BooleanObjectField.class);

      assertNull(result.enabled);
    }

    @Test
    void roundTripsBooleanFields() {
      JsonConverter converter = JsonConverter.defaultInstance();
      BooleanMixObject original = new BooleanMixObject(true, Boolean.FALSE);

      String json = converter.write(original);
      BooleanMixObject restored = converter.read(json, BooleanMixObject.class);

      assertTrue(restored.primitive);
      assertFalse(restored.wrapper);
    }
  }

  @Nested
  class EnumHandling {

    @Test
    void roundTripsSimpleEnum() {
      JsonConverter converter = JsonConverter.defaultInstance();
      EnumObject original = new EnumObject();
      original.status = TestStatus.ACTIVE;

      String json = converter.write(original);
      EnumObject restored = converter.read(json, EnumObject.class);

      assertEquals(TestStatus.ACTIVE, restored.status);
    }

    @Test
    void roundTripsAllEnumValues() {
      JsonConverter converter = JsonConverter.defaultInstance();
      for (TestStatus status : TestStatus.values()) {
        EnumObject original = new EnumObject();
        original.status = status;

        String json = converter.write(original);
        EnumObject restored = converter.read(json, EnumObject.class);

        assertEquals(status, restored.status);
      }
    }

    @Test
    void handlesNullEnum() {
      JsonConverter converter = JsonConverter.defaultInstance();
      String json = "{\"status\":null}";

      EnumObject result = converter.read(json, EnumObject.class);

      assertNull(result.status);
    }
  }

  // --- test helper classes ---

  static class SampleObject {
    String name;
    int count;
    boolean active;

    SampleObject() {}

    SampleObject(String name, int count, boolean active) {
      this.name = name;
      this.count = count;
      this.active = active;
    }
  }

  static class PrivateFieldObject {
    private String value;
    private int number;

    PrivateFieldObject() {}

    PrivateFieldObject(String value, int number) {
      this.value = value;
      this.number = number;
    }

    String getValue() {
      return value;
    }

    int getNumber() {
      return number;
    }
  }

  static class NestedObject {
    String label;
    SampleObject inner;

    NestedObject() {}

    NestedObject(String label, SampleObject inner) {
      this.label = label;
      this.inner = inner;
    }
  }

  static class CollectionObject {
    List<String> items;
    Map<String, String> metadata;

    CollectionObject() {}

    CollectionObject(List<String> items, Map<String, String> metadata) {
      this.items = items;
      this.metadata = metadata;
    }
  }

  static class CamelCaseObject {
    String firstName;
    int maxRetryCount;

    CamelCaseObject() {}

    CamelCaseObject(String firstName, int maxRetryCount) {
      this.firstName = firstName;
      this.maxRetryCount = maxRetryCount;
    }
  }

  static class DateTimeObject {
    LocalDateTime timestamp;

    DateTimeObject() {}
  }

  static class ListOnlyObject {
    List<String> values;

    ListOnlyObject() {}
  }

  static class PrivateFinalFieldObject {
    private final String code;
    private final int amount;

    PrivateFinalFieldObject() {
      this.code = null;
      this.amount = 0;
    }

    PrivateFinalFieldObject(String code, int amount) {
      this.code = code;
      this.amount = amount;
    }

    String getCode() {
      return code;
    }

    int getAmount() {
      return amount;
    }
  }

  static class PrivateFinalBooleanObject {
    private final boolean enabled;
    private final String label;

    PrivateFinalBooleanObject() {
      this.enabled = false;
      this.label = null;
    }

    PrivateFinalBooleanObject(boolean enabled, String label) {
      this.enabled = enabled;
      this.label = label;
    }

    boolean isEnabled() {
      return enabled;
    }

    String getLabel() {
      return label;
    }
  }

  static class SingleValueObject {
    String value;

    SingleValueObject() {}

    SingleValueObject(String value) {
      this.value = value;
    }
  }

  static class ValueObjectContainer {
    SingleValueObject sessionId;
    SingleValueObject tenantId;
    String status;

    ValueObjectContainer() {}

    ValueObjectContainer(SingleValueObject sessionId, SingleValueObject tenantId, String status) {
      this.sessionId = sessionId;
      this.tenantId = tenantId;
      this.status = status;
    }
  }

  static class InstantObject {
    Instant createdAt;

    InstantObject() {}
  }

  static class InstantSnakeCaseObject {
    Instant createdAt;
    Instant expiresAt;

    InstantSnakeCaseObject() {}
  }

  static class BooleanObjectField {
    Boolean enabled;

    BooleanObjectField() {}
  }

  static class BooleanMixObject {
    boolean primitive;
    Boolean wrapper;

    BooleanMixObject() {}

    BooleanMixObject(boolean primitive, Boolean wrapper) {
      this.primitive = primitive;
      this.wrapper = wrapper;
    }
  }

  enum TestStatus {
    ACTIVE,
    EXPIRED,
    TERMINATED
  }

  static class EnumObject {
    TestStatus status;

    EnumObject() {}
  }
}
