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

import java.util.List;
import java.util.Map;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.junit.jupiter.api.Test;

class MappingRuleObjectMapperConditionalTest {

  @Test
  void execute_shouldExecuteRuleWhenConditionIsTrue() {
    String json = "{\"user\": {\"role\": \"admin\", \"name\": \"John\"}}";
    JsonPathWrapper jsonPath = new JsonPathWrapper(json);

    ConditionSpec condition = new ConditionSpec("eq", "$.user.role", "admin");
    MappingRule rule = new MappingRule("$.user.name", "name", condition);
    List<MappingRule> rules = List.of(rule);

    Map<String, Object> result = MappingRuleObjectMapper.execute(rules, jsonPath);

    assertEquals("John", result.get("name"));
  }

  @Test
  void execute_shouldSkipRuleWhenConditionIsFalse() {
    String json = "{\"user\": {\"role\": \"user\", \"name\": \"John\"}}";
    JsonPathWrapper jsonPath = new JsonPathWrapper(json);

    ConditionSpec condition = new ConditionSpec("eq", "$.user.role", "admin");
    MappingRule rule = new MappingRule("$.user.name", "name", condition);
    List<MappingRule> rules = List.of(rule);

    Map<String, Object> result = MappingRuleObjectMapper.execute(rules, jsonPath);

    assertFalse(result.containsKey("name"));
  }

  @Test
  void execute_shouldExecuteRuleWhenNoCondition() {
    String json = "{\"user\": {\"name\": \"John\"}}";
    JsonPathWrapper jsonPath = new JsonPathWrapper(json);

    MappingRule rule = new MappingRule("$.user.name", "name");
    List<MappingRule> rules = List.of(rule);

    Map<String, Object> result = MappingRuleObjectMapper.execute(rules, jsonPath);

    assertEquals("John", result.get("name"));
  }

  @Test
  void execute_shouldExecuteMultipleRulesWithDifferentConditions() {
    String json =
        "{\"user\": {\"role\": \"admin\", \"name\": \"John\", \"verified\": true, \"age\": 30}}";
    JsonPathWrapper jsonPath = new JsonPathWrapper(json);

    ConditionSpec adminCondition = new ConditionSpec("eq", "$.user.role", "admin");
    ConditionSpec verifiedCondition = new ConditionSpec("exists", "$.user.verified");
    ConditionSpec ageCondition = new ConditionSpec("gte", "$.user.age", 18);
    ConditionSpec userCondition = new ConditionSpec("eq", "$.user.role", "user");

    List<MappingRule> rules =
        List.of(
            new MappingRule("$.user.name", "name", adminCondition), // Should execute
            new MappingRule((Object) "admin", "type", verifiedCondition), // Should execute
            new MappingRule("$.user.age", "age", ageCondition), // Should execute
            new MappingRule((Object) "restricted", "access", userCondition) // Should NOT execute
            );

    Map<String, Object> result = MappingRuleObjectMapper.execute(rules, jsonPath);

    assertEquals("John", result.get("name"));
    assertEquals("admin", result.get("type"));
    assertEquals(30, result.get("age"));
    assertFalse(result.containsKey("access"));
  }

  @Test
  void execute_shouldHandleExistsCondition() {
    String json = "{\"user\": {\"name\": \"John\", \"email\": \"john@example.com\"}}";
    JsonPathWrapper jsonPath = new JsonPathWrapper(json);

    ConditionSpec emailCondition = new ConditionSpec("exists", "$.user.email");
    ConditionSpec phoneCondition = new ConditionSpec("exists", "$.user.phone");

    List<MappingRule> rules =
        List.of(
            new MappingRule("$.user.name", "name", emailCondition), // Should execute
            new MappingRule("$.user.name", "backup_name", phoneCondition) // Should NOT execute
            );

    Map<String, Object> result = MappingRuleObjectMapper.execute(rules, jsonPath);

    assertEquals("John", result.get("name"));
    assertFalse(result.containsKey("backup_name"));
  }

  @Test
  void execute_shouldHandleRangeCondition() {
    String json = "{\"users\": [{\"age\": 25}, {\"age\": 15}, {\"age\": 70}]}";
    JsonPathWrapper jsonPath = new JsonPathWrapper(json);

    ConditionSpec adultCondition = new ConditionSpec("gte", "$.users[0].age", 18);
    ConditionSpec minorCondition = new ConditionSpec("gte", "$.users[1].age", 18);
    ConditionSpec seniorCondition = new ConditionSpec("lte", "$.users[2].age", 65);

    List<MappingRule> rules =
        List.of(
            new MappingRule((Object) "adult", "category1", adultCondition), // Should execute
            new MappingRule((Object) "adult", "category2", minorCondition), // Should NOT execute
            new MappingRule((Object) "adult", "category3", seniorCondition) // Should NOT execute
            );

    Map<String, Object> result = MappingRuleObjectMapper.execute(rules, jsonPath);

    assertEquals("adult", result.get("category1"));
    assertFalse(result.containsKey("category2"));
    assertFalse(result.containsKey("category3"));
  }

  @Test
  void execute_shouldHandleRegexCondition() {
    String json =
        "{\"user\": {\"email\": \"john@example.com\", \"invalid_email\": \"not-an-email\"}}";
    JsonPathWrapper jsonPath = new JsonPathWrapper(json);

    String emailPattern = "^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$";
    ConditionSpec validEmailCondition = new ConditionSpec("regex", "$.user.email", emailPattern);
    ConditionSpec invalidEmailCondition =
        new ConditionSpec("regex", "$.user.invalid_email", emailPattern);

    List<MappingRule> rules =
        List.of(
            new MappingRule("$.user.email", "email", validEmailCondition), // Should execute
            new MappingRule(
                "$.user.invalid_email", "backup_email", invalidEmailCondition) // Should NOT execute
            );

    Map<String, Object> result = MappingRuleObjectMapper.execute(rules, jsonPath);

    assertEquals("john@example.com", result.get("email"));
    assertFalse(result.containsKey("backup_email"));
  }

  @Test
  void execute_shouldHandleConditionEvaluationFailure() {
    String json = "{\"user\": {\"name\": \"John\"}}";
    JsonPathWrapper jsonPath = new JsonPathWrapper(json);

    ConditionSpec invalidCondition = new ConditionSpec("exists", "$.invalid[path");
    MappingRule rule = new MappingRule("$.user.name", "name", invalidCondition);
    List<MappingRule> rules = List.of(rule);

    Map<String, Object> result = MappingRuleObjectMapper.execute(rules, jsonPath);

    assertFalse(result.containsKey("name"));
  }

  @Test
  void execute_shouldExecuteConditionalRuleWithStaticValue() {
    String json = "{\"user\": {\"role\": \"admin\"}}";
    JsonPathWrapper jsonPath = new JsonPathWrapper(json);

    ConditionSpec condition = new ConditionSpec("eq", "$.user.role", "admin");
    MappingRule rule = new MappingRule((Object) "administrator", "display_role", condition);
    List<MappingRule> rules = List.of(rule);

    Map<String, Object> result = MappingRuleObjectMapper.execute(rules, jsonPath);

    assertEquals("administrator", result.get("display_role"));
  }

  @Test
  void execute_shouldSkipConditionalRuleWithStaticValue() {
    String json = "{\"user\": {\"role\": \"user\"}}";
    JsonPathWrapper jsonPath = new JsonPathWrapper(json);

    ConditionSpec condition = new ConditionSpec("eq", "$.user.role", "admin");
    MappingRule rule = new MappingRule((Object) "administrator", "display_role", condition);
    List<MappingRule> rules = List.of(rule);

    Map<String, Object> result = MappingRuleObjectMapper.execute(rules, jsonPath);

    assertFalse(result.containsKey("display_role"));
  }

  @Test
  void execute_shouldHandleComplexConditionalScenario() {
    String json =
        """
        {
          "user": {
            "role": "admin",
            "verified": true,
            "age": 30,
            "email": "admin@example.com",
            "permissions": ["read", "write", "delete"]
          }
        }
        """;
    JsonPathWrapper jsonPath = new JsonPathWrapper(json);

    ConditionSpec adminCondition = new ConditionSpec("eq", "$.user.role", "admin");
    ConditionSpec verifiedCondition = new ConditionSpec("exists", "$.user.verified");
    ConditionSpec ageCondition = new ConditionSpec("gte", "$.user.age", 18);
    String emailPattern = "^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$";
    ConditionSpec emailCondition = new ConditionSpec("regex", "$.user.email", emailPattern);
    ConditionSpec userCondition = new ConditionSpec("eq", "$.user.role", "user");

    List<MappingRule> rules =
        List.of(
            new MappingRule("$.user.email", "email", adminCondition), // Should execute
            new MappingRule(
                (Object) "full_access", "access_level", verifiedCondition), // Should execute
            new MappingRule("$.user.age", "age", ageCondition), // Should execute
            new MappingRule("$.user.permissions", "permissions", emailCondition), // Should execute
            new MappingRule((Object) "limited", "access_type", userCondition) // Should NOT execute
            );

    Map<String, Object> result = MappingRuleObjectMapper.execute(rules, jsonPath);

    assertEquals("admin@example.com", result.get("email"));
    assertEquals("full_access", result.get("access_level"));
    assertEquals(30, result.get("age"));
    assertEquals(List.of("read", "write", "delete"), result.get("permissions"));
    assertFalse(result.containsKey("access_type"));
  }
}
