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

package org.idp.server.platform.mapper.functions;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.mapper.MappingRule;
import org.idp.server.platform.mapper.MappingRuleObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * Values are spelled as code points rather than as literals. A composed and a decomposed form of
 * the same character render identically, so a literal would not show which one a case is about, and
 * that distinction is the whole subject of these tests.
 */
public class NormalizeFunctionTest {

  private final NormalizeFunction function = new NormalizeFunction();

  /** HALFWIDTH KATAKANA KA + HALFWIDTH KATAKANA VOICED SOUND MARK. Two code points. */
  private static final String HALFWIDTH_GA = "ｶﾞ";

  /** HALFWIDTH KATAKANA KA, unvoiced. */
  private static final String HALFWIDTH_KA = "ｶ";

  /** KATAKANA LETTER GA, precomposed. One code point. */
  private static final String KATAKANA_GA = "ガ";

  /** KATAKANA LETTER KA + COMBINING KATAKANA-HIRAGANA VOICED SOUND MARK. Renders the same. */
  private static final String KATAKANA_GA_DECOMPOSED = "ガ";

  /** KATAKANA LETTER KA, unvoiced. */
  private static final String KATAKANA_KA = "カ";

  /** HIRAGANA LETTER GA, precomposed. */
  private static final String HIRAGANA_GA = "が";

  /** HIRAGANA LETTER KA + COMBINING KATAKANA-HIRAGANA VOICED SOUND MARK. Renders the same. */
  private static final String HIRAGANA_GA_DECOMPOSED = "が";

  /** "ﾔﾏﾀﾞ ﾀﾛｳ" in halfwidth katakana, words separated by an ASCII space. */
  private static final String HALFWIDTH_NAME = "ﾔﾏﾀﾞ ﾀﾛｳ";

  /** "ヤマダ タロウ" in fullwidth katakana, words separated by an ASCII space. */
  private static final String FULLWIDTH_NAME = "ヤマダ タロウ";

  private static Map<String, Object> args(Object... keyValues) {
    Map<String, Object> args = new HashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      args.put((String) keyValues[i], keyValues[i + 1]);
    }
    return args;
  }

  @Test
  public void testName() {
    assertEquals("normalize", function.name());
  }

  @Test
  public void testApplyWithNullInput() {
    assertNull(function.apply(null, null));
  }

  @Test
  public void testApplyWithEmptyStringInput() {
    assertEquals("", function.apply("", null));
  }

  @Test
  public void testApplyWithNullArgsUsesNfkc() {
    assertEquals(KATAKANA_GA, function.apply(HALFWIDTH_GA, null));
  }

  @Test
  public void testApplyWithEmptyArgsUsesNfkc() {
    assertEquals(KATAKANA_GA, function.apply(HALFWIDTH_GA, Map.of()));
  }

  @Test
  public void testBlankFormFallsBackToTheDefault() {
    assertEquals(KATAKANA_GA, function.apply(HALFWIDTH_GA, args("form", "")));
    assertEquals(KATAKANA_GA, function.apply(HALFWIDTH_GA, args("form", "  ")));
    assertEquals(KATAKANA_GA, function.apply(HALFWIDTH_GA, args("form", null)));
  }

  @Test
  public void testTwoNotationsOfTheSameValueBecomeEqual() {
    // The reason this function exists: one source sends halfwidth katakana and another sends
    // fullwidth. They are different strings until both are normalized, so they cannot be matched,
    // and no combination of replace rules gets there because the voiced halfwidth form is two code
    // points against the fullwidth form's one.
    assertNotEquals(HALFWIDTH_NAME, FULLWIDTH_NAME);

    assertEquals(function.apply(HALFWIDTH_NAME, null), function.apply(FULLWIDTH_NAME, null));
    assertEquals(FULLWIDTH_NAME, function.apply(HALFWIDTH_NAME, null));
  }

  @Test
  public void testNfcComposesButKeepsNotation() {
    // NFC is canonical only: it composes combining marks and never folds notation, so the halfwidth
    // form survives. This is the "canonical representation without loss" case.
    assertEquals(HALFWIDTH_GA, function.apply(HALFWIDTH_GA, args("form", "NFC")));
    assertEquals(HIRAGANA_GA, function.apply(HIRAGANA_GA_DECOMPOSED, args("form", "NFC")));
  }

  @Test
  public void testNfdDecomposesButKeepsNotation() {
    assertEquals(HIRAGANA_GA_DECOMPOSED, function.apply(HIRAGANA_GA, args("form", "NFD")));
    assertEquals(HALFWIDTH_GA, function.apply(HALFWIDTH_GA, args("form", "NFD")));
  }

  @Test
  public void testNfkcAndNfkdBothFoldNotationButDifferInComposition() {
    // Both fold the halfwidth notation; NFKC recomposes afterwards and NFKD does not. The two
    // results render identically and differ only in code points, which is why NFKC is the default:
    // an equality check against a precomposed value would fail under NFKD.
    assertNotEquals(KATAKANA_GA, KATAKANA_GA_DECOMPOSED);

    assertEquals(KATAKANA_GA, function.apply(HALFWIDTH_GA, args("form", "NFKC")));
    assertEquals(KATAKANA_GA_DECOMPOSED, function.apply(HALFWIDTH_GA, args("form", "NFKD")));
  }

  @Test
  public void testFormIsCaseInsensitive() {
    assertEquals(KATAKANA_GA, function.apply(HALFWIDTH_GA, args("form", "nfkc")));
    assertEquals(HALFWIDTH_GA, function.apply(HALFWIDTH_GA, args("form", "nfc")));
  }

  @Test
  public void testNfkcFoldsCompatibilityCharacters() {
    // The lossy side of NFKC, pinned here so the trade-off is visible rather than discovered in
    // production: a display name normalized this way does not round-trip.
    assertEquals("Abc123", function.apply("Ａｂｃ１２３", null));
    assertEquals("1", function.apply("①", null));
    assertEquals("(株)", function.apply("㈱", null));
    assertEquals(" ", function.apply("　", null));
  }

  @Test
  public void testKanaIsNotConvertedBetweenHiraganaAndKatakana() {
    // Out of scope by design: these are distinct characters, not notational variants, and no
    // normalization form maps between them.
    for (String form : List.of("NFC", "NFD", "NFKC", "NFKD")) {
      assertNotEquals(
          function.apply(KATAKANA_GA, args("form", form)),
          function.apply(HIRAGANA_GA, args("form", form)),
          form + " must not fold hiragana and katakana together");
    }
  }

  @Test
  public void testAlreadyNormalizedValueIsUnchanged() {
    assertEquals("yamada taro", function.apply("yamada taro", null));
    assertEquals(KATAKANA_GA, function.apply(KATAKANA_GA, null));
  }

  @Test
  public void testInvalidFormIsRejected() {
    // Returning the value unnormalized would let a comparison that depends on this function pass on
    // unnormalized input, so a typo has to fail rather than degrade.
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> function.apply(HALFWIDTH_GA, args("form", "NFKX")));

    assertTrue(exception.getMessage().contains("NFKX"));
    assertTrue(exception.getMessage().contains("NFKC"));
  }

  @Test
  public void testNonStringInputIsStringified() {
    assertEquals("123", function.apply(123, null));
  }

  @Test
  public void testRegisteredInFunctionRegistry() {
    FunctionRegistry registry = new FunctionRegistry();

    assertTrue(registry.exists("normalize"));
    assertEquals(KATAKANA_GA, registry.get("normalize").apply(HALFWIDTH_GA, null));
  }

  @Test
  public void testAppliesToEachElementThroughMap() {
    // Arrays are the map function's job rather than a second code path here.
    FunctionRegistry registry = new FunctionRegistry();

    Object result =
        registry
            .get("map")
            .apply(List.of(HALFWIDTH_GA, HALFWIDTH_KA), args("function", "normalize"));

    assertEquals(List.of(KATAKANA_GA, KATAKANA_KA), result);
  }

  @Test
  public void testMapPassesArgumentsUnderFunctionArgsOnly() {
    FunctionRegistry registry = new FunctionRegistry();
    ValueFunction map = registry.get("map");

    assertEquals(
        List.of(HALFWIDTH_GA),
        map.apply(
            List.of(HALFWIDTH_GA),
            args("function", "normalize", "function_args", Map.of("form", "NFC"))));

    // Nesting under "args" instead is not an error: map reads function_args and nothing else, so
    // this function receives no arguments and the default NFKC is applied. Same failure class as an
    // invalid form — a comparison succeeds under a form nobody asked for — but it cannot be
    // detected from here, which is why the documented example uses function_args.
    assertEquals(
        List.of(KATAKANA_GA),
        map.apply(
            List.of(HALFWIDTH_GA), args("function", "normalize", "args", Map.of("form", "NFC"))));
  }

  @Test
  public void testNormalizesFromDocumentedConfiguration() {
    // The documented recipe parsed as configuration JSON rather than hand-built, so the snake_case
    // binding and the chain with regex_replace are both covered. Whitespace removal stays a
    // separate function: normalize does Unicode normalization and nothing else.
    String configJson =
        """
        {
          "from": "$.response_body.name",
          "to": "name_normalized",
          "functions": [
            { "name": "normalize", "args": { "form": "NFKC" } },
            { "name": "regex_replace", "args": { "pattern": "[\\\\s\\\\u3000]+", "replacement": "" } }
          ]
        }
        """;
    MappingRule rule = JsonConverter.snakeCaseInstance().read(configJson, MappingRule.class);

    // Halfwidth katakana separated by an IDEOGRAPHIC SPACE, which NFKC folds to an ASCII space and
    // regex_replace then drops.
    String received = "ﾔﾏﾀﾞ　ﾀﾛｳ";

    Map<String, Object> result =
        MappingRuleObjectMapper.execute(
            List.of(rule),
            new JsonPathWrapper("{\"response_body\":{\"name\":\"" + received + "\"}}"));

    assertEquals("ヤマダタロウ", result.get("name_normalized"));
  }
}
