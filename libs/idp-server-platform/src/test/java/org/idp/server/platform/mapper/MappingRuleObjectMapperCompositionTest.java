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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.exception.InvalidConfigurationException;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.junit.jupiter.api.Test;

/** How mapping results are composed: "*" keys and list indexes (Issue #1971). */
class MappingRuleObjectMapperCompositionTest {

  @Test
  void wildcardKeysAreWrittenAsIs() {
    JsonPathWrapper jsonPath =
        new JsonPathWrapper(
            """
            {"body": {"a.b": "dotted", "0": "digit", "list.5": "indexed", "*": {"k": "v"}}}
            """);

    Map<String, Object> result =
        MappingRuleObjectMapper.execute(List.of(new MappingRule("$.body", "*")), jsonPath);

    assertEquals("dotted", result.get("a.b"));
    assertEquals("digit", result.get("0"));
    assertEquals("indexed", result.get("list.5"));
    assertEquals(Map.of("k", "v"), result.get("*"));
    assertFalse(result.containsKey("a"));
    assertFalse(result.containsKey("list"));
  }

  @Test
  void laterRuleReplacesWildcardKeyOfTheSameName() {
    JsonPathWrapper jsonPath =
        new JsonPathWrapper(
            """
            {"body": {"status": "from-body", "name": "Taro"}, "fixed": "from-rule"}
            """);

    Map<String, Object> result =
        MappingRuleObjectMapper.execute(
            List.of(new MappingRule("$.body", "*"), new MappingRule("$.fixed", "status")),
            jsonPath);

    assertEquals("from-rule", result.get("status"));
    assertEquals("Taro", result.get("name"));
  }

  @Test
  void pathKeyOfTheSameNameTakesThePositionOfTheWildcardKey() {
    JsonPathWrapper jsonPath =
        new JsonPathWrapper(
            """
            {"body": {"a": {"x": 1}}, "b": 2, "replacement": {"y": 3}}
            """);

    Map<String, Object> result =
        MappingRuleObjectMapper.execute(
            List.of(
                new MappingRule("$.body", "*"),
                new MappingRule("$.b", "a.b"),
                new MappingRule("$.replacement", "a")),
            jsonPath);

    assertEquals(Map.of("y", 3, "b", 2), result.get("a"));
  }

  @Test
  void pathRuleCanWriteIntoWildcardMap() {
    JsonPathWrapper jsonPath =
        new JsonPathWrapper(
            """
            {"body": {"claims": {"given_name": "Taro"}}, "family": "Yamada"}
            """);

    Map<String, Object> result =
        MappingRuleObjectMapper.execute(
            List.of(
                new MappingRule("$.body", "*"), new MappingRule("$.family", "claims.family_name")),
            jsonPath);

    assertEquals(Map.of("given_name", "Taro", "family_name", "Yamada"), result.get("claims"));
  }

  @Test
  void indexSkippedByConditionLeavesNull() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"first\": \"a\", \"second\": \"b\"}");
    ConditionSpec never = new ConditionSpec("exists", "$.missing", null);

    Map<String, Object> result =
        MappingRuleObjectMapper.execute(
            List.of(
                new MappingRule("$.first", "items.0", never),
                new MappingRule("$.second", "items.1")),
            jsonPath);

    assertEquals(Arrays.asList(null, "b"), result.get("items"));
  }

  @Test
  void indexUpToTheLimitIsAllowed() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"value\": \"last\"}");

    Map<String, Object> result =
        MappingRuleObjectMapper.execute(
            List.of(new MappingRule("$.value", "items." + ObjectCompositor.MAX_LIST_INDEX)),
            jsonPath);

    List<?> items = (List<?>) result.get("items");
    assertEquals(ObjectCompositor.MAX_LIST_INDEX + 1, items.size());
    assertEquals("last", items.get(ObjectCompositor.MAX_LIST_INDEX));
  }

  @Test
  void indexWithLeadingZerosIsReadAsANumber() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"value\": \"b\"}");

    Map<String, Object> result =
        MappingRuleObjectMapper.execute(
            List.of(new MappingRule("$.value", "items.0001")), jsonPath);

    assertEquals(Arrays.asList(null, "b"), result.get("items"));
  }

  @Test
  void indexOverTheLimitIsRejected() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"value\": \"x\"}");

    for (String index :
        List.of(String.valueOf(ObjectCompositor.MAX_LIST_INDEX + 1), "99999999999999999999")) {
      List<MappingRule> rules = List.of(new MappingRule("$.value", "items." + index + ".name"));
      assertThrows(
          InvalidConfigurationException.class,
          () -> MappingRuleObjectMapper.execute(rules, jsonPath));
    }
  }

  @Test
  void pathKeepsNestedListsAndMaps() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"type\": \"document\", \"txn\": \"t-1\"}");

    Map<String, Object> result =
        MappingRuleObjectMapper.execute(
            List.of(
                new MappingRule("$.type", "verification.evidence.0.type"),
                new MappingRule("$.txn", "verification.evidence.0.check_details.0.txn")),
            jsonPath);

    assertEquals(
        Map.of(
            "evidence",
            List.of(Map.of("type", "document", "check_details", List.of(Map.of("txn", "t-1"))))),
        result.get("verification"));
  }
}
