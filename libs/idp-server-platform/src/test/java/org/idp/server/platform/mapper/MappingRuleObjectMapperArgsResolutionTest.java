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

import java.util.*;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MappingRuleObjectMapperArgsResolutionTest {

  @Nested
  @DisplayName("resolveArgs - JSONPath resolution in function args")
  class ResolveArgsTests {

    @Test
    void resolveArgs_returnsNull_whenArgsIsNull() {
      JsonPathWrapper jsonPath = new JsonPathWrapper("{}");
      Map<String, Object> result = MappingRuleObjectMapper.resolveArgs(null, jsonPath);
      assertNull(result);
    }

    @Test
    void resolveArgs_returnsEmpty_whenArgsIsEmpty() {
      JsonPathWrapper jsonPath = new JsonPathWrapper("{}");
      Map<String, Object> result = MappingRuleObjectMapper.resolveArgs(Map.of(), jsonPath);
      assertTrue(result.isEmpty());
    }

    @Test
    void resolveArgs_preservesStaticValues() {
      JsonPathWrapper jsonPath = new JsonPathWrapper("{\"name\": \"Alice\"}");
      Map<String, Object> args = Map.of("value", "static_string", "count", 42);
      Map<String, Object> result = MappingRuleObjectMapper.resolveArgs(args, jsonPath);
      assertEquals("static_string", result.get("value"));
      assertEquals(42, result.get("count"));
    }

    @Test
    void resolveArgs_resolvesJsonPathString() {
      String json = "{\"request_body\": {\"new_item\": \"account_999\"}}";
      JsonPathWrapper jsonPath = new JsonPathWrapper(json);
      Map<String, Object> args = new HashMap<>();
      args.put("value", "$.request_body.new_item");
      Map<String, Object> result = MappingRuleObjectMapper.resolveArgs(args, jsonPath);
      assertEquals("account_999", result.get("value"));
    }

    @Test
    void resolveArgs_resolvesJsonPathArray() {
      String json = "{\"request_body\": {\"items\": [\"a\", \"b\", \"c\"]}}";
      JsonPathWrapper jsonPath = new JsonPathWrapper(json);
      Map<String, Object> args = new HashMap<>();
      args.put("source", "$.request_body.items");
      Map<String, Object> result = MappingRuleObjectMapper.resolveArgs(args, jsonPath);
      assertEquals(List.of("a", "b", "c"), result.get("source"));
    }

    @Test
    void resolveArgs_resolvesJsonPathObjectArray() {
      String json =
          """
          {
            "request_body": {
              "new_accounts": [
                {"account_no": "333", "type": "investment"},
                {"account_no": "444", "type": "savings"}
              ]
            }
          }
          """;
      JsonPathWrapper jsonPath = new JsonPathWrapper(json);
      Map<String, Object> args = new HashMap<>();
      args.put("source", "$.request_body.new_accounts");
      args.put("key", "account_no");
      Map<String, Object> result = MappingRuleObjectMapper.resolveArgs(args, jsonPath);

      List<?> source = (List<?>) result.get("source");
      assertEquals(2, source.size());
      assertEquals("account_no", result.get("key"));
    }

    @Test
    void resolveArgs_mixesStaticAndDynamicValues() {
      String json = "{\"data\": {\"name\": \"Bob\"}}";
      JsonPathWrapper jsonPath = new JsonPathWrapper(json);
      Map<String, Object> args = new HashMap<>();
      args.put("value", "$.data.name");
      args.put("separator", ",");
      args.put("flag", true);
      Map<String, Object> result = MappingRuleObjectMapper.resolveArgs(args, jsonPath);
      assertEquals("Bob", result.get("value"));
      assertEquals(",", result.get("separator"));
      assertEquals(true, result.get("flag"));
    }

    @Test
    void resolveArgs_resolvesToNull_whenPathNotFound() {
      JsonPathWrapper jsonPath = new JsonPathWrapper("{\"data\": {}}");
      Map<String, Object> args = new HashMap<>();
      args.put("value", "$.data.nonexistent");
      Map<String, Object> result = MappingRuleObjectMapper.resolveArgs(args, jsonPath);
      assertNull(result.get("value"));
    }
  }

  @Nested
  @DisplayName("End-to-end: merge function with dynamic source from context")
  class MergeWithDynamicSourceTests {

    @Test
    void merge_withDynamicSource_mergesArraysFromContext() {
      String json =
          """
          {
            "user": {
              "verified_claims": {
                "claims": {
                  "accounts": [
                    {"account_no": "111", "type": "savings"},
                    {"account_no": "222", "type": "checking"}
                  ]
                }
              }
            },
            "request_body": {
              "new_accounts": [
                {"account_no": "333", "type": "investment"}
              ]
            }
          }
          """;

      JsonPathWrapper jsonPath = new JsonPathWrapper(json);

      List<MappingRule> rules =
          List.of(
              new MappingRule(
                  "$.user.verified_claims.claims.accounts",
                  "claims.accounts",
                  List.of(
                      new FunctionSpec(
                          "merge",
                          Map.of("source", "$.request_body.new_accounts", "key", "account_no")))));

      Map<String, Object> result = MappingRuleObjectMapper.execute(rules, jsonPath);

      Map<String, Object> claims = (Map<String, Object>) result.get("claims");
      List<?> accounts = (List<?>) claims.get("accounts");
      assertEquals(3, accounts.size());
    }

    @Test
    void merge_withDynamicSource_deduplicatesByKey() {
      String json =
          """
          {
            "existing": [
              {"id": "1", "value": "old"},
              {"id": "2", "value": "keep"}
            ],
            "incoming": [
              {"id": "1", "value": "updated"},
              {"id": "3", "value": "new"}
            ]
          }
          """;

      JsonPathWrapper jsonPath = new JsonPathWrapper(json);

      List<MappingRule> rules =
          List.of(
              new MappingRule(
                  "$.existing",
                  "result",
                  List.of(new FunctionSpec("merge", Map.of("source", "$.incoming", "key", "id")))));

      Map<String, Object> result = MappingRuleObjectMapper.execute(rules, jsonPath);

      List<?> merged = (List<?>) result.get("result");
      assertEquals(3, merged.size());

      @SuppressWarnings("unchecked")
      Map<String, Object> first = (Map<String, Object>) merged.get(0);
      assertEquals("updated", first.get("value"));
    }
  }

  @Nested
  @DisplayName("End-to-end: append function with dynamic value from context")
  class AppendWithDynamicValueTests {

    @Test
    void append_withDynamicValue_appendsValueFromContext() {
      String json =
          """
          {
            "user": {
              "verified_claims": {
                "claims": {
                  "tags": ["finance", "personal"]
                }
              }
            },
            "request_body": {
              "new_tag": "investment"
            }
          }
          """;

      JsonPathWrapper jsonPath = new JsonPathWrapper(json);

      List<MappingRule> rules =
          List.of(
              new MappingRule(
                  "$.user.verified_claims.claims.tags",
                  "claims.tags",
                  List.of(new FunctionSpec("append", Map.of("value", "$.request_body.new_tag")))));

      Map<String, Object> result = MappingRuleObjectMapper.execute(rules, jsonPath);

      Map<String, Object> claims = (Map<String, Object>) result.get("claims");
      List<?> tags = (List<?>) claims.get("tags");
      assertEquals(List.of("finance", "personal", "investment"), tags);
    }
  }

  @Nested
  @DisplayName("End-to-end: pluck function (no dynamic args needed, but verify compatibility)")
  class PluckCompatibilityTests {

    @Test
    void pluck_worksWithStaticArgs_afterResolveArgsChange() {
      String json =
          """
          {
            "accounts": [
              {"account_no": "123", "type": "savings"},
              {"account_no": "456", "type": "checking"}
            ]
          }
          """;

      JsonPathWrapper jsonPath = new JsonPathWrapper(json);

      List<MappingRule> rules =
          List.of(
              new MappingRule(
                  "$.accounts",
                  "account_numbers",
                  List.of(new FunctionSpec("pluck", Map.of("field", "account_no")))));

      Map<String, Object> result = MappingRuleObjectMapper.execute(rules, jsonPath);

      assertEquals(List.of("123", "456"), result.get("account_numbers"));
    }
  }
}
