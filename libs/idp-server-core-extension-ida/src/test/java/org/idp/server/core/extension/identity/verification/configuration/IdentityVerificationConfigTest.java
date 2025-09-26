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

package org.idp.server.core.extension.identity.verification.configuration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.idp.server.platform.mapper.ConditionSpec;
import org.junit.jupiter.api.Test;

class IdentityVerificationConfigTest {

  @Test
  void testConfigWithoutCondition() {
    Map<String, Object> details = Map.of("key", "value");
    IdentityVerificationConfig config = new IdentityVerificationConfig("test_type", details);

    assertEquals("test_type", config.type());
    assertEquals(details, config.details());
    assertNull(config.condition());
    assertFalse(config.hasCondition());
  }

  @Test
  void testConfigWithCondition() {
    Map<String, Object> details = Map.of("key", "value");
    ConditionSpec condition = new ConditionSpec("eq", "$.user.role", "admin");
    IdentityVerificationConfig config =
        new IdentityVerificationConfig("test_type", details, condition);

    assertEquals("test_type", config.type());
    assertEquals(details, config.details());
    assertEquals(condition, config.condition());
    assertTrue(config.hasCondition());
  }

  @Test
  void testToMapWithoutCondition() {
    Map<String, Object> details = Map.of("key", "value");
    IdentityVerificationConfig config = new IdentityVerificationConfig("test_type", details);

    Map<String, Object> map = config.toMap();

    assertEquals("test_type", map.get("type"));
    assertEquals(details, map.get("details"));
    assertFalse(map.containsKey("condition"));
  }

  @Test
  void testToMapWithCondition() {
    Map<String, Object> details = Map.of("key", "value");
    ConditionSpec condition = new ConditionSpec("eq", "$.user.role", "admin");
    IdentityVerificationConfig config =
        new IdentityVerificationConfig("test_type", details, condition);

    Map<String, Object> map = config.toMap();

    assertEquals("test_type", map.get("type"));
    assertEquals(details, map.get("details"));
    assertTrue(map.containsKey("condition"));
    assertEquals(condition.toMap(), map.get("condition"));
  }

  @Test
  void testConditionSpecWithComplexConditions() {
    Map<String, Object> details = Map.of("verification_level", "premium");

    // Test allOf condition
    ConditionSpec allOfCondition =
        new ConditionSpec(
            "allOf",
            null,
            java.util.List.of(
                new ConditionSpec("eq", "$.user.tier", "premium"),
                new ConditionSpec("gte", "$.user.age", 18)));

    IdentityVerificationConfig configWithAllOf =
        new IdentityVerificationConfig("premium_verification", details, allOfCondition);

    assertTrue(configWithAllOf.hasCondition());
    assertEquals("allOf", configWithAllOf.condition().operation());

    // Test anyOf condition
    ConditionSpec anyOfCondition =
        new ConditionSpec(
            "anyOf",
            null,
            java.util.List.of(
                new ConditionSpec("eq", "$.user.role", "admin"),
                new ConditionSpec("eq", "$.user.role", "editor")));

    IdentityVerificationConfig configWithAnyOf =
        new IdentityVerificationConfig("flexible_verification", details, anyOfCondition);

    assertTrue(configWithAnyOf.hasCondition());
    assertEquals("anyOf", configWithAnyOf.condition().operation());
  }

  @Test
  void testErrorHandlingStrategyWithCondition() {
    Map<String, Object> details =
        Map.of(
            "verification_level", "premium",
            "error_strategy", "RESILIENT");
    ConditionSpec condition = new ConditionSpec("eq", "$.user.verified", true);

    IdentityVerificationConfig config =
        new IdentityVerificationConfig("test_verification", details, condition);

    assertEquals(ErrorHandlingStrategy.RESILIENT, config.errorHandlingStrategy());
    assertTrue(config.hasCondition());
    assertEquals(condition, config.condition());
  }
}
