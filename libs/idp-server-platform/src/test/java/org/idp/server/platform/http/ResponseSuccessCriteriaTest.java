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

package org.idp.server.platform.http;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResponseSuccessCriteriaTest {

  @Test
  @DisplayName("evaluate returns true when no conditions are defined")
  void testEvaluateWithNoConditions() {
    ResponseSuccessCriteria criteria = ResponseSuccessCriteria.empty();
    JsonPathWrapper json = new JsonPathWrapper("{\"status\": \"success\"}");

    boolean result = criteria.evaluate(json);

    assertTrue(result);
  }

  @Test
  @DisplayName("evaluate returns true when single EQ condition matches")
  void testEvaluateWithMatchingEqCondition() {
    ResponseCondition condition = new ResponseCondition();
    condition.path = "$.status";
    condition.operation = "eq";
    condition.value = "success";

    ResponseSuccessCriteria criteria =
        new ResponseSuccessCriteria(List.of(condition), ConditionMatchMode.ALL);

    JsonPathWrapper json = new JsonPathWrapper("{\"status\": \"success\"}");

    boolean result = criteria.evaluate(json);

    assertTrue(result);
  }

  @Test
  @DisplayName("evaluate returns false when single EQ condition does not match")
  void testEvaluateWithNonMatchingEqCondition() {
    ResponseCondition condition = new ResponseCondition();
    condition.path = "$.status";
    condition.operation = "eq";
    condition.value = "success";

    ResponseSuccessCriteria criteria =
        new ResponseSuccessCriteria(List.of(condition), ConditionMatchMode.ALL);

    JsonPathWrapper json = new JsonPathWrapper("{\"status\": \"failed\"}");

    boolean result = criteria.evaluate(json);

    assertFalse(result);
  }

  @Test
  @DisplayName("evaluate returns true when EXISTS condition matches")
  void testEvaluateWithExistsCondition() {
    ResponseCondition condition = new ResponseCondition();
    condition.path = "$.data.id";
    condition.operation = "exists";
    condition.value = null;

    ResponseSuccessCriteria criteria =
        new ResponseSuccessCriteria(List.of(condition), ConditionMatchMode.ALL);

    JsonPathWrapper json = new JsonPathWrapper("{\"data\": {\"id\": \"123\"}}");

    boolean result = criteria.evaluate(json);

    assertTrue(result);
  }

  @Test
  @DisplayName("evaluate returns false when EXISTS condition does not match")
  void testEvaluateWithExistsConditionNotFound() {
    ResponseCondition condition = new ResponseCondition();
    condition.path = "$.error";
    condition.operation = "exists";
    condition.value = null;

    ResponseSuccessCriteria criteria =
        new ResponseSuccessCriteria(List.of(condition), ConditionMatchMode.ALL);

    JsonPathWrapper json = new JsonPathWrapper("{\"status\": \"success\"}");

    boolean result = criteria.evaluate(json);

    assertFalse(result);
  }

  @Test
  @DisplayName("evaluate returns true when MISSING condition matches")
  void testEvaluateWithMissingCondition() {
    ResponseCondition condition = new ResponseCondition();
    condition.path = "$.error";
    condition.operation = "missing";
    condition.value = null;

    ResponseSuccessCriteria criteria =
        new ResponseSuccessCriteria(List.of(condition), ConditionMatchMode.ALL);

    JsonPathWrapper json = new JsonPathWrapper("{\"status\": \"success\"}");

    boolean result = criteria.evaluate(json);

    assertTrue(result);
  }

  @Test
  @DisplayName("evaluate returns false when MISSING condition does not match")
  void testEvaluateWithMissingConditionFound() {
    ResponseCondition condition = new ResponseCondition();
    condition.path = "$.error";
    condition.operation = "missing";
    condition.value = null;

    ResponseSuccessCriteria criteria =
        new ResponseSuccessCriteria(List.of(condition), ConditionMatchMode.ALL);

    JsonPathWrapper json = new JsonPathWrapper("{\"error\": \"something went wrong\"}");

    boolean result = criteria.evaluate(json);

    assertFalse(result);
  }

  @Test
  @DisplayName("evaluate returns true when ALL mode and all conditions match")
  void testEvaluateWithAllModeAndAllMatch() {
    ResponseCondition condition1 = new ResponseCondition();
    condition1.path = "$.status";
    condition1.operation = "eq";
    condition1.value = "success";

    ResponseCondition condition2 = new ResponseCondition();
    condition2.path = "$.error";
    condition2.operation = "missing";
    condition2.value = null;

    ResponseSuccessCriteria criteria =
        new ResponseSuccessCriteria(List.of(condition1, condition2), ConditionMatchMode.ALL);

    JsonPathWrapper json = new JsonPathWrapper("{\"status\": \"success\"}");

    boolean result = criteria.evaluate(json);

    assertTrue(result);
  }

  @Test
  @DisplayName("evaluate returns false when ALL mode and one condition fails")
  void testEvaluateWithAllModeAndOneFails() {
    ResponseCondition condition1 = new ResponseCondition();
    condition1.path = "$.status";
    condition1.operation = "eq";
    condition1.value = "success";

    ResponseCondition condition2 = new ResponseCondition();
    condition2.path = "$.error";
    condition2.operation = "missing";
    condition2.value = null;

    ResponseSuccessCriteria criteria =
        new ResponseSuccessCriteria(List.of(condition1, condition2), ConditionMatchMode.ALL);

    JsonPathWrapper json = new JsonPathWrapper("{\"status\": \"success\", \"error\": \"failed\"}");

    boolean result = criteria.evaluate(json);

    assertFalse(result);
  }

  @Test
  @DisplayName("evaluate returns true when ANY mode and at least one condition matches")
  void testEvaluateWithAnyModeAndOneMatches() {
    ResponseCondition condition1 = new ResponseCondition();
    condition1.path = "$.status";
    condition1.operation = "eq";
    condition1.value = "success";

    ResponseCondition condition2 = new ResponseCondition();
    condition2.path = "$.state";
    condition2.operation = "eq";
    condition2.value = "completed";

    ResponseSuccessCriteria criteria =
        new ResponseSuccessCriteria(List.of(condition1, condition2), ConditionMatchMode.ANY);

    JsonPathWrapper json = new JsonPathWrapper("{\"status\": \"success\", \"state\": \"pending\"}");

    boolean result = criteria.evaluate(json);

    assertTrue(result);
  }

  @Test
  @DisplayName("evaluate returns false when ANY mode and no conditions match")
  void testEvaluateWithAnyModeAndNoneMatch() {
    ResponseCondition condition1 = new ResponseCondition();
    condition1.path = "$.status";
    condition1.operation = "eq";
    condition1.value = "success";

    ResponseCondition condition2 = new ResponseCondition();
    condition2.path = "$.state";
    condition2.operation = "eq";
    condition2.value = "completed";

    ResponseSuccessCriteria criteria =
        new ResponseSuccessCriteria(List.of(condition1, condition2), ConditionMatchMode.ANY);

    JsonPathWrapper json = new JsonPathWrapper("{\"status\": \"failed\", \"state\": \"pending\"}");

    boolean result = criteria.evaluate(json);

    assertFalse(result);
  }

  @Test
  @DisplayName("empty returns criteria that always evaluates to true")
  void testEmptyReturnsCriteriaAlwaysTrue() {
    ResponseSuccessCriteria criteria = ResponseSuccessCriteria.empty();

    assertTrue(criteria.evaluate(new JsonPathWrapper("{\"status\": \"success\"}")));
    assertTrue(criteria.evaluate(new JsonPathWrapper("{\"error\": \"failed\"}")));
    assertTrue(criteria.evaluate(new JsonPathWrapper("{}")));
  }

  @Test
  @DisplayName("evaluate handles nested JSON paths correctly")
  void testEvaluateWithNestedPath() {
    ResponseCondition condition = new ResponseCondition();
    condition.path = "$.data.verification.status";
    condition.operation = "eq";
    condition.value = "approved";

    ResponseSuccessCriteria criteria =
        new ResponseSuccessCriteria(List.of(condition), ConditionMatchMode.ALL);

    JsonPathWrapper json =
        new JsonPathWrapper(
            "{\"data\": {\"verification\": {\"status\": \"approved\", \"id\": \"123\"}}}");

    boolean result = criteria.evaluate(json);

    assertTrue(result);
  }

  @Test
  @DisplayName("toMap returns correct structure")
  void testToMap() {
    ResponseCondition condition = new ResponseCondition();
    condition.path = "$.status";
    condition.operation = "eq";
    condition.value = "success";

    ResponseSuccessCriteria criteria =
        new ResponseSuccessCriteria(List.of(condition), ConditionMatchMode.ALL);

    var map = criteria.toMap();

    assertNotNull(map);
    assertTrue(map.containsKey("conditions"));
    assertTrue(map.containsKey("match_mode"));
    assertEquals("ALL", map.get("match_mode"));
  }
}
