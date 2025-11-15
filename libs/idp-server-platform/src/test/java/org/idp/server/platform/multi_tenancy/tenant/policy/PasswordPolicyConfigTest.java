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

package org.idp.server.platform.multi_tenancy.tenant.policy;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PasswordPolicyConfigTest {

  @Nested
  @DisplayName("Default policy")
  class DefaultPolicy {

    @Test
    @DisplayName("Should have minimum 8 characters")
    void testDefaultMinLength() {
      PasswordPolicyConfig config = PasswordPolicyConfig.defaultPolicy();

      assertEquals(8, config.minLength());
    }

    @Test
    @DisplayName("Should not require uppercase by default")
    void testDefaultNoUppercase() {
      PasswordPolicyConfig config = PasswordPolicyConfig.defaultPolicy();

      assertFalse(config.requireUppercase());
    }

    @Test
    @DisplayName("Should not require lowercase by default")
    void testDefaultNoLowercase() {
      PasswordPolicyConfig config = PasswordPolicyConfig.defaultPolicy();

      assertFalse(config.requireLowercase());
    }

    @Test
    @DisplayName("Should not require number by default")
    void testDefaultNoNumber() {
      PasswordPolicyConfig config = PasswordPolicyConfig.defaultPolicy();

      assertFalse(config.requireNumber());
    }

    @Test
    @DisplayName("Should not require special char by default")
    void testDefaultNoSpecialChar() {
      PasswordPolicyConfig config = PasswordPolicyConfig.defaultPolicy();

      assertFalse(config.requireSpecialChar());
    }

    @Test
    @DisplayName("Should have maxHistory of 0 by default")
    void testDefaultMaxHistory() {
      PasswordPolicyConfig config = PasswordPolicyConfig.defaultPolicy();

      assertEquals(0, config.maxHistory());
    }

    @Test
    @DisplayName("Should exist by default")
    void testDefaultExists() {
      PasswordPolicyConfig config = PasswordPolicyConfig.defaultPolicy();

      assertTrue(config.exists());
    }
  }

  @Nested
  @DisplayName("fromMap() deserialization")
  class FromMapDeserialization {

    @Test
    @DisplayName("Should return default policy when map is null")
    void testNullMapReturnsDefault() {
      PasswordPolicyConfig config = PasswordPolicyConfig.fromMap(null);

      assertEquals(8, config.minLength());
      assertFalse(config.requireUppercase());
      assertFalse(config.requireLowercase());
      assertFalse(config.requireNumber());
      assertFalse(config.requireSpecialChar());
    }

    @Test
    @DisplayName("Should return default policy when map is empty")
    void testEmptyMapReturnsDefault() {
      PasswordPolicyConfig config = PasswordPolicyConfig.fromMap(new HashMap<>());

      assertEquals(8, config.minLength());
      assertFalse(config.requireUppercase());
      assertFalse(config.requireLowercase());
      assertFalse(config.requireNumber());
      assertFalse(config.requireSpecialChar());
    }

    @Test
    @DisplayName("Should parse custom minimum length")
    void testCustomMinLength() {
      Map<String, Object> map = new HashMap<>();
      map.put("min_length", 16);

      PasswordPolicyConfig config = PasswordPolicyConfig.fromMap(map);

      assertEquals(16, config.minLength());
    }

    @Test
    @DisplayName("Should parse require_uppercase flag")
    void testRequireUppercase() {
      Map<String, Object> map = new HashMap<>();
      map.put("require_uppercase", true);

      PasswordPolicyConfig config = PasswordPolicyConfig.fromMap(map);

      assertTrue(config.requireUppercase());
    }

    @Test
    @DisplayName("Should parse require_lowercase flag")
    void testRequireLowercase() {
      Map<String, Object> map = new HashMap<>();
      map.put("require_lowercase", true);

      PasswordPolicyConfig config = PasswordPolicyConfig.fromMap(map);

      assertTrue(config.requireLowercase());
    }

    @Test
    @DisplayName("Should parse require_number flag")
    void testRequireNumber() {
      Map<String, Object> map = new HashMap<>();
      map.put("require_number", true);

      PasswordPolicyConfig config = PasswordPolicyConfig.fromMap(map);

      assertTrue(config.requireNumber());
    }

    @Test
    @DisplayName("Should parse require_special_char flag")
    void testRequireSpecialChar() {
      Map<String, Object> map = new HashMap<>();
      map.put("require_special_char", true);

      PasswordPolicyConfig config = PasswordPolicyConfig.fromMap(map);

      assertTrue(config.requireSpecialChar());
    }

    @Test
    @DisplayName("Should parse max_history")
    void testMaxHistory() {
      Map<String, Object> map = new HashMap<>();
      map.put("max_history", 5);

      PasswordPolicyConfig config = PasswordPolicyConfig.fromMap(map);

      assertEquals(5, config.maxHistory());
    }

    @Test
    @DisplayName("Should parse custom_regex")
    void testCustomRegex() {
      Map<String, Object> map = new HashMap<>();
      map.put("custom_regex", ".*idp.*");

      PasswordPolicyConfig config = PasswordPolicyConfig.fromMap(map);

      assertEquals(".*idp.*", config.customRegex());
    }

    @Test
    @DisplayName("Should parse custom_regex_error_message")
    void testCustomRegexErrorMessage() {
      Map<String, Object> map = new HashMap<>();
      map.put("custom_regex", ".*idp.*");
      map.put("custom_regex_error_message", "Password must contain 'idp'");

      PasswordPolicyConfig config = PasswordPolicyConfig.fromMap(map);

      assertEquals(".*idp.*", config.customRegex());
      assertEquals("Password must contain 'idp'", config.customRegexErrorMessage());
    }

    @Test
    @DisplayName("Should parse all settings combined")
    void testAllSettingsCombined() {
      Map<String, Object> map = new HashMap<>();
      map.put("min_length", 12);
      map.put("require_uppercase", true);
      map.put("require_lowercase", true);
      map.put("require_number", true);
      map.put("require_special_char", true);
      map.put("max_history", 10);

      PasswordPolicyConfig config = PasswordPolicyConfig.fromMap(map);

      assertEquals(12, config.minLength());
      assertTrue(config.requireUppercase());
      assertTrue(config.requireLowercase());
      assertTrue(config.requireNumber());
      assertTrue(config.requireSpecialChar());
      assertEquals(10, config.maxHistory());
    }

    @Test
    @DisplayName("Should use default values for missing keys")
    void testMissingKeysUseDefaults() {
      Map<String, Object> map = new HashMap<>();
      map.put("min_length", 10);
      // Other fields not specified

      PasswordPolicyConfig config = PasswordPolicyConfig.fromMap(map);

      assertEquals(10, config.minLength());
      assertFalse(config.requireUppercase());
      assertFalse(config.requireLowercase());
      assertFalse(config.requireNumber());
      assertFalse(config.requireSpecialChar());
      assertEquals(0, config.maxHistory());
    }

    @Test
    @DisplayName("Should handle integer as Number subtype")
    void testIntegerAsNumber() {
      Map<String, Object> map = new HashMap<>();
      map.put("min_length", Integer.valueOf(20));

      PasswordPolicyConfig config = PasswordPolicyConfig.fromMap(map);

      assertEquals(20, config.minLength());
    }

    @Test
    @DisplayName("Should handle Long as Number subtype")
    void testLongAsNumber() {
      Map<String, Object> map = new HashMap<>();
      map.put("min_length", Long.valueOf(25));

      PasswordPolicyConfig config = PasswordPolicyConfig.fromMap(map);

      assertEquals(25, config.minLength());
    }

    @Test
    @DisplayName("Should handle invalid type for integer field (fallback to default)")
    void testInvalidTypeForInteger() {
      Map<String, Object> map = new HashMap<>();
      map.put("min_length", "not a number");

      PasswordPolicyConfig config = PasswordPolicyConfig.fromMap(map);

      assertEquals(8, config.minLength()); // Should use default
    }

    @Test
    @DisplayName("Should handle invalid type for boolean field (fallback to default)")
    void testInvalidTypeForBoolean() {
      Map<String, Object> map = new HashMap<>();
      map.put("require_uppercase", "not a boolean");

      PasswordPolicyConfig config = PasswordPolicyConfig.fromMap(map);

      assertFalse(config.requireUppercase()); // Should use default
    }
  }

  @Nested
  @DisplayName("toMap() serialization")
  class ToMapSerialization {

    @Test
    @DisplayName("Should serialize default policy")
    void testSerializeDefaultPolicy() {
      PasswordPolicyConfig config = PasswordPolicyConfig.defaultPolicy();

      Map<String, Object> map = config.toMap();

      assertEquals(8, map.get("min_length"));
      assertEquals(false, map.get("require_uppercase"));
      assertEquals(false, map.get("require_lowercase"));
      assertEquals(false, map.get("require_number"));
      assertEquals(false, map.get("require_special_char"));
      assertEquals(0, map.get("max_history"));
    }

    @Test
    @DisplayName("Should serialize custom policy")
    void testSerializeCustomPolicy() {
      PasswordPolicyConfig config =
          new PasswordPolicyConfig(12, true, true, true, true, ".*idp.*", "Must contain 'idp'", 5);

      Map<String, Object> map = config.toMap();

      assertEquals(12, map.get("min_length"));
      assertEquals(true, map.get("require_uppercase"));
      assertEquals(true, map.get("require_lowercase"));
      assertEquals(true, map.get("require_number"));
      assertEquals(true, map.get("require_special_char"));
      assertEquals(".*idp.*", map.get("custom_regex"));
      assertEquals("Must contain 'idp'", map.get("custom_regex_error_message"));
      assertEquals(5, map.get("max_history"));
    }

    @Test
    @DisplayName("Should not include custom_regex keys when null")
    void testSerializeWithoutCustomRegex() {
      PasswordPolicyConfig config = new PasswordPolicyConfig(8, false, false, false, false, 0);

      Map<String, Object> map = config.toMap();

      assertFalse(map.containsKey("custom_regex"));
      assertFalse(map.containsKey("custom_regex_error_message"));
    }

    @Test
    @DisplayName("Should have all required keys")
    void testAllKeysPresent() {
      PasswordPolicyConfig config = PasswordPolicyConfig.defaultPolicy();

      Map<String, Object> map = config.toMap();

      assertTrue(map.containsKey("min_length"));
      assertTrue(map.containsKey("require_uppercase"));
      assertTrue(map.containsKey("require_lowercase"));
      assertTrue(map.containsKey("require_number"));
      assertTrue(map.containsKey("require_special_char"));
      assertTrue(map.containsKey("max_history"));
    }
  }

  @Nested
  @DisplayName("Round-trip serialization")
  class RoundTripSerialization {

    @Test
    @DisplayName("Should survive round-trip for default policy")
    void testRoundTripDefault() {
      PasswordPolicyConfig original = PasswordPolicyConfig.defaultPolicy();

      Map<String, Object> map = original.toMap();
      PasswordPolicyConfig restored = PasswordPolicyConfig.fromMap(map);

      assertEquals(original.minLength(), restored.minLength());
      assertEquals(original.requireUppercase(), restored.requireUppercase());
      assertEquals(original.requireLowercase(), restored.requireLowercase());
      assertEquals(original.requireNumber(), restored.requireNumber());
      assertEquals(original.requireSpecialChar(), restored.requireSpecialChar());
      assertEquals(original.maxHistory(), restored.maxHistory());
    }

    @Test
    @DisplayName("Should survive round-trip for custom policy")
    void testRoundTripCustom() {
      PasswordPolicyConfig original = new PasswordPolicyConfig(16, true, true, false, true, 10);

      Map<String, Object> map = original.toMap();
      PasswordPolicyConfig restored = PasswordPolicyConfig.fromMap(map);

      assertEquals(original.minLength(), restored.minLength());
      assertEquals(original.requireUppercase(), restored.requireUppercase());
      assertEquals(original.requireLowercase(), restored.requireLowercase());
      assertEquals(original.requireNumber(), restored.requireNumber());
      assertEquals(original.requireSpecialChar(), restored.requireSpecialChar());
      assertEquals(original.maxHistory(), restored.maxHistory());
    }

    @Test
    @DisplayName("Should survive round-trip for all-enabled policy")
    void testRoundTripAllEnabled() {
      PasswordPolicyConfig original = new PasswordPolicyConfig(20, true, true, true, true, 15);

      Map<String, Object> map = original.toMap();
      PasswordPolicyConfig restored = PasswordPolicyConfig.fromMap(map);

      assertEquals(original.minLength(), restored.minLength());
      assertEquals(original.requireUppercase(), restored.requireUppercase());
      assertEquals(original.requireLowercase(), restored.requireLowercase());
      assertEquals(original.requireNumber(), restored.requireNumber());
      assertEquals(original.requireSpecialChar(), restored.requireSpecialChar());
      assertEquals(original.maxHistory(), restored.maxHistory());
    }

    @Test
    @DisplayName("Should survive round-trip for policy with custom regex")
    void testRoundTripWithCustomRegex() {
      PasswordPolicyConfig original =
          new PasswordPolicyConfig(
              12, true, true, false, false, ".*idp.*", "Must contain 'idp'", 5);

      Map<String, Object> map = original.toMap();
      PasswordPolicyConfig restored = PasswordPolicyConfig.fromMap(map);

      assertEquals(original.minLength(), restored.minLength());
      assertEquals(original.requireUppercase(), restored.requireUppercase());
      assertEquals(original.requireLowercase(), restored.requireLowercase());
      assertEquals(original.requireNumber(), restored.requireNumber());
      assertEquals(original.requireSpecialChar(), restored.requireSpecialChar());
      assertEquals(original.customRegex(), restored.customRegex());
      assertEquals(original.customRegexErrorMessage(), restored.customRegexErrorMessage());
      assertEquals(original.maxHistory(), restored.maxHistory());
    }
  }
}
