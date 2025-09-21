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

package org.idp.server.platform.mapper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.junit.jupiter.api.Test;

class MappingRuleConditionalTest {

  @Test
  void shouldExecute_shouldReturnTrueWhenNoCondition() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"name\": \"John\"}}");
    MappingRule rule = new MappingRule("$.user.name", "name");

    assertTrue(rule.shouldExecute(jsonPath));
  }

  @Test
  void shouldExecute_shouldReturnTrueWhenConditionIsTrue() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"role\": \"admin\"}}");
    ConditionSpec condition = new ConditionSpec("eq", "$.user.role", "admin");
    MappingRule rule = new MappingRule("$.user.name", "name", condition);

    assertTrue(rule.shouldExecute(jsonPath));
  }

  @Test
  void shouldExecute_shouldReturnFalseWhenConditionIsFalse() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"role\": \"user\"}}");
    ConditionSpec condition = new ConditionSpec("eq", "$.user.role", "admin");
    MappingRule rule = new MappingRule("$.user.name", "name", condition);

    assertFalse(rule.shouldExecute(jsonPath));
  }

  @Test
  void shouldExecute_shouldReturnFalseOnConditionEvaluationException() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"name\": \"John\"}}");
    ConditionSpec condition = new ConditionSpec("exists", "$.invalid[path");
    MappingRule rule = new MappingRule("$.user.name", "name", condition);

    assertFalse(rule.shouldExecute(jsonPath));
  }

  @Test
  void hasCondition_shouldReturnTrueWhenConditionExists() {
    ConditionSpec condition = new ConditionSpec("exists", "$.user.verified");
    MappingRule rule = new MappingRule("$.user.name", "name", condition);

    assertTrue(rule.hasCondition());
  }

  @Test
  void hasCondition_shouldReturnFalseWhenConditionIsNull() {
    MappingRule rule = new MappingRule("$.user.name", "name");

    assertFalse(rule.hasCondition());
  }

  @Test
  void condition_shouldReturnConditionWhenSet() {
    ConditionSpec condition = new ConditionSpec("exists", "$.user.verified");
    MappingRule rule = new MappingRule("$.user.name", "name", condition);

    assertEquals(condition, rule.condition());
  }

  @Test
  void condition_shouldReturnNullWhenNotSet() {
    MappingRule rule = new MappingRule("$.user.name", "name");

    assertNull(rule.condition());
  }

  @Test
  void toMap_shouldIncludeConditionWhenPresent() {
    ConditionSpec condition = new ConditionSpec("equals", "$.user.role", "admin");
    MappingRule rule = new MappingRule("$.user.name", "name", condition);

    Map<String, Object> result = rule.toMap();

    assertNotNull(result.get("condition"));
    assertEquals(condition.toMap(), result.get("condition"));
  }

  @Test
  void toMap_shouldIncludeNullConditionWhenNotPresent() {
    MappingRule rule = new MappingRule("$.user.name", "name");

    Map<String, Object> result = rule.toMap();

    assertNull(result.get("condition"));
  }

  @Test
  void constructorWithStaticValueAndCondition_shouldWorkCorrectly() {
    ConditionSpec condition = new ConditionSpec("exists", "$.user.verified");
    MappingRule rule = new MappingRule((Object) "admin", "role", condition);

    assertFalse(rule.hasFrom()); // Should be false
    assertTrue(rule.hasStaticValue());
    assertEquals("admin", rule.staticValue());
    assertFalse(rule.hasFunctions());
    assertTrue(rule.hasCondition());
    assertEquals(condition, rule.condition());
  }

  @Test
  void constructorWithFunctionsAndCondition_shouldWorkCorrectly() {
    ConditionSpec condition = new ConditionSpec("exists", "$.user.verified");
    MappingRule rule = new MappingRule("$.user.name", "name", null, condition);

    assertTrue(rule.hasFrom());
    assertEquals("$.user.name", rule.from());
    assertFalse(rule.hasFunctions());
    assertTrue(rule.hasCondition());
    assertEquals(condition, rule.condition());
  }

  @Test
  void constructorWithStaticValueFunctionsAndCondition_shouldWorkCorrectly() {
    ConditionSpec condition = new ConditionSpec("exists", "$.user.verified");
    MappingRule rule = new MappingRule((Object) "admin", "role", null, condition);

    assertFalse(rule.hasFrom());
    assertTrue(rule.hasStaticValue());
    assertEquals("admin", rule.staticValue());
    assertFalse(rule.hasFunctions());
    assertTrue(rule.hasCondition());
    assertEquals(condition, rule.condition());
  }
}
