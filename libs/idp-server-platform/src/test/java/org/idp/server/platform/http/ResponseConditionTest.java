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

import org.idp.server.platform.condition.ConditionOperation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResponseConditionTest {

  @Test
  @DisplayName("operation() converts string to ConditionOperation enum")
  void testOperationConvertsStringToEnum() {
    ResponseCondition condition = new ResponseCondition();
    condition.operation = "eq";

    ConditionOperation result = condition.operation();

    assertEquals(ConditionOperation.EQ, result);
  }

  @Test
  @DisplayName("operation() handles all valid operation strings")
  void testOperationHandlesAllValidStrings() {
    String[] validOperations = {
      "eq", "ne", "gt", "lt", "gte", "lte", "in", "nin", "exists", "missing", "contains", "regex"
    };

    for (String op : validOperations) {
      ResponseCondition condition = new ResponseCondition();
      condition.operation = op;

      ConditionOperation result = condition.operation();

      assertNotEquals(
          ConditionOperation.UNKNOWN, result, "Operation '" + op + "' should be recognized");
    }
  }

  @Test
  @DisplayName("operation() returns UNKNOWN for invalid operation string")
  void testOperationReturnsUnknownForInvalid() {
    ResponseCondition condition = new ResponseCondition();
    condition.operation = "invalid_operation";

    ConditionOperation result = condition.operation();

    assertEquals(ConditionOperation.UNKNOWN, result);
  }

  @Test
  @DisplayName("operation() is case-insensitive")
  void testOperationIsCaseInsensitive() {
    ResponseCondition condition1 = new ResponseCondition();
    condition1.operation = "EQ";

    ResponseCondition condition2 = new ResponseCondition();
    condition2.operation = "eq";

    ResponseCondition condition3 = new ResponseCondition();
    condition3.operation = "Eq";

    assertEquals(ConditionOperation.EQ, condition1.operation());
    assertEquals(ConditionOperation.EQ, condition2.operation());
    assertEquals(ConditionOperation.EQ, condition3.operation());
  }

  @Test
  @DisplayName("constructor from ConditionOperation enum stores lowercase string")
  void testConstructorFromEnumStoresLowercase() {
    ResponseCondition condition =
        new ResponseCondition("$.status", ConditionOperation.EQ, "success");

    assertEquals("eq", condition.operation);
    assertEquals("$.status", condition.path);
    assertEquals("success", condition.value);
  }

  @Test
  @DisplayName("path() returns the stored path")
  void testPathReturnsStoredValue() {
    ResponseCondition condition = new ResponseCondition();
    condition.path = "$.data.status";

    assertEquals("$.data.status", condition.path());
  }

  @Test
  @DisplayName("value() returns the stored value")
  void testValueReturnsStoredValue() {
    ResponseCondition condition = new ResponseCondition();
    condition.value = "expected_value";

    assertEquals("expected_value", condition.value());
  }

  @Test
  @DisplayName("value() can store null")
  void testValueCanStoreNull() {
    ResponseCondition condition = new ResponseCondition();
    condition.value = null;

    assertNull(condition.value());
  }

  @Test
  @DisplayName("value() can store different types")
  void testValueCanStoreDifferentTypes() {
    ResponseCondition stringCondition = new ResponseCondition();
    stringCondition.value = "string_value";
    assertEquals("string_value", stringCondition.value());

    ResponseCondition intCondition = new ResponseCondition();
    intCondition.value = 123;
    assertEquals(123, intCondition.value());

    ResponseCondition boolCondition = new ResponseCondition();
    boolCondition.value = true;
    assertEquals(true, boolCondition.value());
  }

  @Test
  @DisplayName("default constructor creates empty condition")
  void testDefaultConstructor() {
    ResponseCondition condition = new ResponseCondition();

    assertNull(condition.path);
    assertNull(condition.operation);
    assertNull(condition.value);
  }
}
