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

  @Nested
  @DisplayName("Composite: map(reshape) → merge chain")
  class CompositeReshapeMergeTests {

    @Test
    void reshape_then_merge_normalizesAndAccumulatesEntities() {
      // ユースケース: 外部APIレスポンス(別schema)をreshapeで正規化し、既存データにmerge
      String json =
          """
          {
            "user": {
              "custom_properties": {
                "entities": [
                  {"id": "1", "name": "Existing Corp", "type": "organization"}
                ]
              }
            },
            "response_body": {
              "items": [
                {"entity_id": "2", "entity_name": "New Inc", "kind": "organization"},
                {"entity_id": "3", "entity_name": "Third LLC", "kind": "person"}
              ]
            }
          }
          """;

      JsonPathWrapper jsonPath = new JsonPathWrapper(json);

      // map(reshape) → merge: 外部レスポンスを正規化して既存entitiesにマージ
      List<MappingRule> rules =
          List.of(
              new MappingRule(
                  "$.response_body.items",
                  "entities",
                  List.of(
                      new FunctionSpec(
                          "map",
                          Map.of(
                              "function",
                              "reshape",
                              "function_args",
                              Map.of(
                                  "fields",
                                  Map.of(
                                      "id", "$.entity_id",
                                      "name", "$.entity_name",
                                      "type", "$.kind")))),
                      new FunctionSpec(
                          "merge",
                          Map.of(
                              "source", "$.user.custom_properties.entities",
                              "key", "id")))));

      Map<String, Object> result = MappingRuleObjectMapper.execute(rules, jsonPath);

      @SuppressWarnings("unchecked")
      List<Map<String, Object>> entities = (List<Map<String, Object>>) result.get("entities");

      // reshape で正規化された2件 + 既存1件 = 3件（id重複なし）
      assertEquals(3, entities.size());

      // reshape された新規データが正しい shape
      Map<String, Object> entity2 =
          entities.stream().filter(e -> "2".equals(e.get("id"))).findFirst().orElseThrow();
      assertEquals("New Inc", entity2.get("name"));
      assertEquals("organization", entity2.get("type"));

      Map<String, Object> entity3 =
          entities.stream().filter(e -> "3".equals(e.get("id"))).findFirst().orElseThrow();
      assertEquals("Third LLC", entity3.get("name"));
      assertEquals("person", entity3.get("type"));

      // 既存データもマージされている
      Map<String, Object> entity1 =
          entities.stream().filter(e -> "1".equals(e.get("id"))).findFirst().orElseThrow();
      assertEquals("Existing Corp", entity1.get("name"));
      assertEquals("organization", entity1.get("type"));
    }

    @Test
    void reshape_then_merge_deduplicatesByKey() {
      // ユースケース: 既存エンティティのID重複時は後勝ちで更新
      // merge の動作: input(from) + source を結合し、key重複は後勝ち
      // 新データで上書きしたいなら: from=既存, source=新データ(reshape後)
      String json =
          """
          {
            "user": {
              "custom_properties": {
                "accounts": [
                  {"id": "A001", "name": "Old Name", "type": "savings"}
                ]
              }
            },
            "response_body": {
              "bank_accounts": [
                {"account_code": "A001", "account_name": "Updated Name", "account_type": "premium_savings"},
                {"account_code": "A002", "account_name": "New Account", "account_type": "checking"}
              ]
            }
          }
          """;

      JsonPathWrapper jsonPath = new JsonPathWrapper(json);

      // from=既存accounts(input), reshape後の新データをsourceに渡すパターンは
      // 現状のfunction chainでは直接表現できないため、
      // from=新データ(reshape後), source=既存 で merge し、source(既存)が後勝ちになることを確認
      List<MappingRule> rules =
          List.of(
              new MappingRule(
                  "$.response_body.bank_accounts",
                  "accounts",
                  List.of(
                      new FunctionSpec(
                          "map",
                          Map.of(
                              "function",
                              "reshape",
                              "function_args",
                              Map.of(
                                  "fields",
                                  Map.of(
                                      "id", "$.account_code",
                                      "name", "$.account_name",
                                      "type", "$.account_type")))),
                      new FunctionSpec(
                          "merge",
                          Map.of(
                              "source", "$.user.custom_properties.accounts",
                              "key", "id")))));

      Map<String, Object> result = MappingRuleObjectMapper.execute(rules, jsonPath);

      @SuppressWarnings("unchecked")
      List<Map<String, Object>> accounts = (List<Map<String, Object>>) result.get("accounts");

      // merge: [reshape後 A001, A002] + [既存 A001] → key "A001" は後勝ち(既存)
      assertEquals(2, accounts.size());

      // A001 は source(既存) が後勝ちで残る
      Map<String, Object> a001 =
          accounts.stream().filter(a -> "A001".equals(a.get("id"))).findFirst().orElseThrow();
      assertEquals("Old Name", a001.get("name"));
      assertEquals("savings", a001.get("type"));

      // A002 は新規
      Map<String, Object> a002 =
          accounts.stream().filter(a -> "A002".equals(a.get("id"))).findFirst().orElseThrow();
      assertEquals("New Account", a002.get("name"));
      assertEquals("checking", a002.get("type"));
    }

    @Test
    void reshape_then_pluck_extractsFieldAfterRename() {
      // reshape → pluck: リネーム後にフラット配列を抽出
      String json =
          """
          {
            "items": [
              {"code": "JP001", "label": "Tokyo"},
              {"code": "JP002", "label": "Osaka"}
            ]
          }
          """;

      JsonPathWrapper jsonPath = new JsonPathWrapper(json);

      List<MappingRule> rules =
          List.of(
              new MappingRule(
                  "$.items",
                  "region_codes",
                  List.of(
                      new FunctionSpec(
                          "map",
                          Map.of(
                              "function",
                              "reshape",
                              "function_args",
                              Map.of("fields", Map.of("id", "$.code", "name", "$.label")))),
                      new FunctionSpec("pluck", Map.of("field", "id")))));

      Map<String, Object> result = MappingRuleObjectMapper.execute(rules, jsonPath);

      assertEquals(List.of("JP001", "JP002"), result.get("region_codes"));
    }

    @Test
    void reshape_then_filter_then_merge_fullPipeline() {
      // ユースケース: 外部APIレスポンスをreshape → typeでfilter → 既存データにmerge
      String json =
          """
          {
            "user": {
              "custom_properties": {
                "accounts": [
                  {"id": "A001", "name": "Existing Savings", "type": "savings"}
                ]
              }
            },
            "response_body": {
              "bank_accounts": [
                {"account_code": "A002", "account_name": "Checking", "account_type": "checking"},
                {"account_code": "A003", "account_name": "New Savings", "account_type": "savings"},
                {"account_code": "A004", "account_name": "Investment", "account_type": "investment"}
              ]
            }
          }
          """;

      JsonPathWrapper jsonPath = new JsonPathWrapper(json);

      // reshape → filter(savings のみ) → merge
      List<MappingRule> rules =
          List.of(
              new MappingRule(
                  "$.response_body.bank_accounts",
                  "accounts",
                  List.of(
                      new FunctionSpec(
                          "map",
                          Map.of(
                              "function",
                              "reshape",
                              "function_args",
                              Map.of(
                                  "fields",
                                  Map.of(
                                      "id", "$.account_code",
                                      "name", "$.account_name",
                                      "type", "$.account_type")))),
                      new FunctionSpec(
                          "filter", Map.of("field", "type", "condition", "{{value}} == 'savings'")),
                      new FunctionSpec(
                          "merge",
                          Map.of(
                              "source", "$.user.custom_properties.accounts",
                              "key", "id")))));

      Map<String, Object> result = MappingRuleObjectMapper.execute(rules, jsonPath);

      @SuppressWarnings("unchecked")
      List<Map<String, Object>> accounts = (List<Map<String, Object>>) result.get("accounts");

      // filter で savings のみ残る → A003 のみ。merge で既存 A001 を追加 → 2件
      assertEquals(2, accounts.size());

      Map<String, Object> a003 =
          accounts.stream().filter(a -> "A003".equals(a.get("id"))).findFirst().orElseThrow();
      assertEquals("New Savings", a003.get("name"));
      assertEquals("savings", a003.get("type"));

      Map<String, Object> a001 =
          accounts.stream().filter(a -> "A001".equals(a.get("id"))).findFirst().orElseThrow();
      assertEquals("Existing Savings", a001.get("name"));
      assertEquals("savings", a001.get("type"));
    }

    @Test
    void reshape_then_filter_then_pluck_extractsFilteredIds() {
      // reshape → filter → pluck: 条件付きIDリスト抽出
      String json =
          """
          {
            "items": [
              {"code": "JP001", "label": "Tokyo", "status": "active"},
              {"code": "JP002", "label": "Osaka", "status": "inactive"},
              {"code": "JP003", "label": "Nagoya", "status": "active"}
            ]
          }
          """;

      JsonPathWrapper jsonPath = new JsonPathWrapper(json);

      List<MappingRule> rules =
          List.of(
              new MappingRule(
                  "$.items",
                  "active_codes",
                  List.of(
                      new FunctionSpec(
                          "map",
                          Map.of(
                              "function",
                              "reshape",
                              "function_args",
                              Map.of(
                                  "fields",
                                  Map.of(
                                      "id", "$.code",
                                      "name", "$.label",
                                      "status", "$.status")))),
                      new FunctionSpec(
                          "filter",
                          Map.of("field", "status", "condition", "{{value}} == 'active'")),
                      new FunctionSpec("pluck", Map.of("field", "id")))));

      Map<String, Object> result = MappingRuleObjectMapper.execute(rules, jsonPath);

      assertEquals(List.of("JP001", "JP003"), result.get("active_codes"));
    }
  }
}
