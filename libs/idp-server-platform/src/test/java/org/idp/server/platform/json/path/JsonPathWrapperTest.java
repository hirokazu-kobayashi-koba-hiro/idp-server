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

package org.idp.server.platform.json.path;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pins the Jayway JSONPath behaviors that mapping rule {@code from} / dynamic args / condition
 * {@code path} expressions depend on (#1757).
 *
 * <p>Two kinds of behavior are pinned:
 *
 * <ul>
 *   <li><b>Common usage</b> — dotted access, array index, wildcard projection, basic filter
 *       predicates, as used across config templates and examples.
 *   <li><b>Cross-element predicates</b> — root references and nested predicates inside {@code
 *       [?(...)]}, which let a single {@code from} expression filter a list against a reference
 *       element (e.g. keep only elements sharing an attribute with the {@code primary == true}
 *       element). These are not part of a formal JSONPath spec, so this test exists to fail when a
 *       json-path version upgrade changes them — it does not verify the Jayway implementation
 *       itself.
 * </ul>
 *
 * <p>The source data mirrors the originating use case: an external API response list under {@code
 * $.execution_http_requests[0].response_body.list}.
 */
class JsonPathWrapperTest {

  static final String LIST = "$.execution_http_requests[0].response_body.list";

  static final String SOURCE =
      """
      {
        "request_body": {
          "email": "user@example.com",
          "age": 30,
          "verified": true,
          "tags": ["finance", "personal"]
        },
        "password-authentication": {"success_count": 1},
        "execution_http_requests": [
          {
            "response_body": {
              "id": "req-1",
              "list": [
                {"primary": true,  "properties": {"holder_name": "TARO"}},
                {"primary": false, "properties": {"holder_name": "TARO"}},
                {"primary": false, "properties": {"holder_name": "HANAKO"}}
              ]
            }
          }
        ]
      }
      """;

  JsonPathWrapper wrapper = new JsonPathWrapper(SOURCE);

  private List<String> holderNames(List<Map<String, Object>> elements) {
    return elements.stream()
        .map(e -> (Map<String, Object>) e.get("properties"))
        .map(p -> (String) p.get("holder_name"))
        .toList();
  }

  @Nested
  class CommonUsagePatterns {

    @Test
    void dottedAccessReadsNestedScalar() {
      assertEquals("user@example.com", wrapper.readAsString("$.request_body.email"));
      assertEquals(30, wrapper.readAsInt("$.request_body.age"));
      assertEquals(true, wrapper.readAsBoolean("$.request_body.verified"));
    }

    @Test
    void dottedAccessReadsWholeObjectAsMap() {
      Map<String, Object> result = wrapper.readAsMap("$.request_body");

      assertEquals("user@example.com", result.get("email"));
      assertEquals(30, result.get("age"));
    }

    @Test
    void dottedAccessReadsArrayAsList() {
      assertEquals(List.of("finance", "personal"), wrapper.readAsStringList("$.request_body.tags"));
    }

    /** Hyphenated keys work in dot notation (used by condition paths like success_count). */
    @Test
    void hyphenatedKeyWorksInDotNotation() {
      assertEquals(1, wrapper.readAsInt("$.password-authentication.success_count"));
    }

    @Test
    void arrayIndexAccessReadsElement() {
      assertEquals("req-1", wrapper.readAsString("$.execution_http_requests[0].response_body.id"));
      assertEquals(true, wrapper.readAsBoolean(LIST + "[0].primary"));
    }

    /** Wildcard projection flattens one field out of every element. */
    @Test
    void wildcardProjectionCollectsFieldValues() {
      assertEquals(
          List.of("TARO", "TARO", "HANAKO"),
          wrapper.readAsStringList(LIST + "[*].properties.holder_name"));
    }
  }

  @Nested
  class BasicFilterPredicates {

    @Test
    void filterByBooleanFieldMatches() {
      List<Map<String, Object>> result = wrapper.readAsMapList(LIST + "[?(@.primary == true)]");

      assertEquals(List.of("TARO"), holderNames(result));
    }

    @Test
    void filterByStringFieldMatches() {
      List<Map<String, Object>> result =
          wrapper.readAsMapList(LIST + "[?(@.properties.holder_name == 'HANAKO')]");

      assertEquals(1, result.size());
      assertEquals(false, result.get(0).get("primary"));
    }

    @Test
    void compoundPredicateWithAndMatches() {
      List<Map<String, Object>> result =
          wrapper.readAsMapList(
              LIST + "[?(@.primary == false && @.properties.holder_name == 'TARO')]");

      assertEquals(List.of("TARO"), holderNames(result));
    }

    /** A projection after a filter resolves to the list of matched elements' field values. */
    @Test
    void projectionAfterFilterCollectsFieldValues() {
      assertEquals(
          List.of("TARO"),
          wrapper.readAsStringList(LIST + "[?(@.primary == true)].properties.holder_name"));
    }
  }

  @Nested
  class CrossElementPredicates {

    /** Pattern A: root reference with an index inside a predicate. */
    @Test
    void rootReferenceWithIndexInPredicateMatches() {
      List<Map<String, Object>> result =
          wrapper.readAsMapList(
              LIST + "[?(@.properties.holder_name == " + LIST + "[0].properties.holder_name)]");

      assertEquals(List.of("TARO", "TARO"), holderNames(result));
    }

    /** Pattern B: {@code in} against a nested-predicate root reference. */
    @Test
    void inAgainstNestedPredicateMatches() {
      List<Map<String, Object>> result =
          wrapper.readAsMapList(
              LIST
                  + "[?(@.properties.holder_name in "
                  + LIST
                  + "[?(@.primary == true)].properties.holder_name)]");

      assertEquals(List.of("TARO", "TARO"), holderNames(result));
    }

    /** Pattern D: {@code contains} with the list-resolving expression on the left-hand side. */
    @Test
    void containsWithListOnLeftHandSideMatches() {
      List<Map<String, Object>> result =
          wrapper.readAsMapList(
              LIST
                  + "[?("
                  + LIST
                  + "[?(@.primary == true)].properties.holder_name"
                  + " contains @.properties.holder_name)]");

      assertEquals(List.of("TARO", "TARO"), holderNames(result));
    }

    /** Pattern F: {@code !(in)} selects the complement of pattern B. */
    @Test
    void negatedInAgainstNestedPredicateMatches() {
      List<Map<String, Object>> result =
          wrapper.readAsMapList(
              LIST
                  + "[?(!(@.properties.holder_name in "
                  + LIST
                  + "[?(@.primary == true)].properties.holder_name))]");

      assertEquals(List.of("HANAKO"), holderNames(result));
    }

    /**
     * Pattern E: {@code nin} against a nested predicate. In json-path 2.9.0 {@code nin} is
     * implemented as the exact negation of {@code in}, so it selects the same complement as {@code
     * !(in)} (pattern F). Issue #1757 reported this as silently empty, which does not reproduce
     * here — pinned so a future version change in either direction is caught.
     */
    @Test
    void ninAgainstNestedPredicateSelectsComplement() {
      List<Map<String, Object>> result =
          wrapper.readAsMapList(
              LIST
                  + "[?(@.properties.holder_name nin "
                  + LIST
                  + "[?(@.primary == true)].properties.holder_name)]");

      assertEquals(List.of("HANAKO"), holderNames(result));
    }
  }

  @Nested
  class SilentlyEmptyTraps {

    /**
     * Pattern C: a nested predicate resolves to a list, so {@code ==} never matches. This returns
     * an empty list instead of raising an error — configurations must use {@code in}.
     */
    @Test
    void equalsAgainstNestedPredicateIsSilentlyEmpty() {
      List<Map<String, Object>> result =
          wrapper.readAsMapList(
              LIST
                  + "[?(@.properties.holder_name == "
                  + LIST
                  + "[?(@.primary == true)].properties.holder_name)]");

      assertEquals(List.of(), result);
    }
  }

  @Nested
  class FailClosedBehavior {

    /** A filter that matches nothing yields an empty list, not an error. */
    @Test
    void filterWithNoMatchReturnsEmptyList() {
      List<Map<String, Object>> result =
          wrapper.readAsMapList(LIST + "[?(@.properties.holder_name == 'NOBODY')]");

      assertEquals(List.of(), result);
    }

    /** A filter referencing a property absent from every element yields an empty list. */
    @Test
    void filterOnMissingPropertyReturnsEmptyList() {
      List<Map<String, Object>> result = wrapper.readAsMapList(LIST + "[?(@.nonexistent == 'x')]");

      assertEquals(List.of(), result);
    }

    /** A missing definite path resolves to null (PathNotFoundException is absorbed). */
    @Test
    void missingDefinitePathReturnsNull() {
      assertNull(wrapper.readRaw("$.nonexistent.path"));
    }

    /** An out-of-range array index also resolves to null. */
    @Test
    void outOfRangeIndexReturnsNull() {
      assertNull(wrapper.readRaw("$.execution_http_requests[9].response_body"));
    }
  }
}
