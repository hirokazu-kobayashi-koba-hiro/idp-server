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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class ValueFunctionChainTest {

  @Test
  void appliesFunctionsInOrder() {
    ValueFunctionChain chain =
        ValueFunctionChain.forComparison(
            List.of(
                new FunctionSpec("normalize", Map.of("form", "NFKC")),
                new FunctionSpec(
                    "regex_replace", Map.of("pattern", "[\\s\\u3000]+", "replacement", "")),
                new FunctionSpec("kana", Map.of("to", "katakana"))));

    assertEquals("ヤマダタロウ", chain.apply("やまだ　たろう"));
    assertEquals("ヤマダタロウ", chain.apply("ﾔﾏﾀﾞ ﾀﾛｳ"));
  }

  @Test
  void unknownFunctionIsAnErrorWhenBuilt() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ValueFunctionChain.forComparison(
                List.of(new FunctionSpec("no_such_function", Map.of()))));
    assertThrows(
        IllegalArgumentException.class,
        () -> ValueFunctionChain.forComparison(List.of(new FunctionSpec(null, Map.of()))));
    // A null element (e.g. "functions": [null] in a configuration) is the same configuration error,
    // not a NullPointerException.
    List<FunctionSpec> withNull = new ArrayList<>();
    withNull.add(null);
    assertThrows(IllegalArgumentException.class, () -> ValueFunctionChain.forComparison(withNull));
  }

  @Test
  void functionsThatWouldBreakAComparisonAreNotAllowed() {
    // exists makes any two non-empty values equal; random_string and now make no two equal.
    for (String name : List.of("exists", "random_string", "now", "uuid4", "if", "format")) {
      IllegalArgumentException error =
          assertThrows(
              IllegalArgumentException.class,
              () -> ValueFunctionChain.forComparison(List.of(new FunctionSpec(name, Map.of()))));
      assertTrue(error.getMessage().startsWith("function not allowed here"), error.getMessage());
    }
  }

  @Test
  void aCallerCanAllowItsOwnSetOfFunctions() {
    ValueFunctionChain chain =
        ValueFunctionChain.of(List.of(new FunctionSpec("trim", Map.of())), Set.of("trim"));

    assertEquals("a", chain.apply(" a "));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ValueFunctionChain.of(
                List.of(new FunctionSpec("case", Map.of("mode", "lower"))), Set.of("trim")));
  }

  @Test
  void wrongArgumentsAreFoundWhenBuiltNotOnTheFirstValue() {
    List<FunctionSpec> wrong =
        List.of(
            new FunctionSpec("kana", Map.of("to", "katakan")),
            new FunctionSpec("date", Map.of("format", "uuuu-MM-dd HH:mm")),
            new FunctionSpec("case", Map.of("mode", "snake")),
            new FunctionSpec("case", Map.of()),
            new FunctionSpec("normalize", Map.of("form", "NFKX")),
            new FunctionSpec("regex_replace", Map.of("pattern", "[0-9]")));

    for (FunctionSpec spec : wrong) {
      IllegalArgumentException error =
          assertThrows(
              IllegalArgumentException.class,
              () -> ValueFunctionChain.forComparison(List.of(spec)),
              spec.name() + " " + spec.args());
      assertTrue(error.getMessage().startsWith("invalid arguments"), error.getMessage());
    }
  }

  @Test
  void argumentsAreUsedAsWrittenWithoutResolvingPaths() {
    // A mapping rule would resolve "$.x" against its source; here it is the literal replacement.
    ValueFunctionChain chain =
        ValueFunctionChain.forComparison(
            List.of(new FunctionSpec("replace", Map.of("target", "a", "replacement", "$.x"))));

    assertEquals("$.xbc", chain.apply("abc"));
  }

  @Test
  void anEmptyChainReturnsTheInput() {
    ValueFunctionChain chain = ValueFunctionChain.forComparison(null);

    assertTrue(chain.isEmpty());
    assertEquals("abc", chain.apply("abc"));
  }
}
