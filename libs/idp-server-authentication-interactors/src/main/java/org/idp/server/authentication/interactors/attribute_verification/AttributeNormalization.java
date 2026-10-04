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

package org.idp.server.authentication.interactors.attribute_verification;

import java.util.List;
import java.util.Map;
import org.idp.server.platform.mapper.FunctionSpec;
import org.idp.server.platform.mapper.ValueFunctionChain;

/**
 * Named ways of bringing a submitted value and a registered value to the same form before they are
 * compared (Issue #1935).
 *
 * <p>Each is a fixed chain of the platform's mapping functions, so the same transformations can be
 * written out as {@code functions} when none of these fits. Applied to both sides alike, so a
 * registered value stored in a different form from the one the end-user types — fullwidth, a
 * hyphenated phone number, a date written with 年月日, a reading in hiragana — still matches.
 *
 * <p>These fold notation, which the platform warns against for comparisons that decide which
 * principal a request is about. That is not what happens here: the user is already identified, and
 * the comparison only asks whether what they typed agrees with what their own account holds.
 * Folding widens what that one user can type; it cannot make the value of another user match.
 */
public enum AttributeNormalization {

  /** Compared as given. */
  EXACT("exact", List.of()),

  /** Unicode NFKC, then trimmed: fullwidth and halfwidth forms compare equal. */
  NFKC("nfkc", List.of(normalizeNfkc(), new FunctionSpec("trim", Map.of()))),

  /** NFKC, then every non-digit removed: {@code 090-1234-5678} and {@code ０９０１２３４５６７８}. */
  DIGITS("digits", List.of(normalizeNfkc(), regexReplace("[^0-9]", ""))),

  /**
   * Read as a calendar date and written as {@code yyyy-MM-dd}: {@code 1990/4/1}, {@code 19900401},
   * {@code 1990.4.1} and {@code 1990年4月1日}, fullwidth or not. See the platform's {@code date}.
   */
  DATE("date", List.of(new FunctionSpec("date", Map.of()))),

  /**
   * A name in Latin or kanji script: NFKC, every space removed, dashes unified, lower case. {@code
   * Smith‐Jones} (U+2010), {@code ＳＭＩＴＨ－ＪＯＮＥＳ} and {@code smith - jones} match. The prolonged sound
   * mark ー is not a dash and is kept.
   */
  NAME("name", nameChain()),

  /** A reading: as {@link #NAME}, then hiragana as katakana. {@code やまだ} and {@code ﾔﾏﾀﾞ} match. */
  KANA("kana", kanaChain()),

  /** An email address: NFKC, trimmed, lower case. */
  EMAIL(
      "email",
      List.of(
          normalizeNfkc(),
          new FunctionSpec("trim", Map.of()),
          new FunctionSpec("case", Map.of("mode", "lower"))));

  String value;
  ValueFunctionChain chain;

  AttributeNormalization(String value, List<FunctionSpec> functions) {
    this.value = value;
    this.chain = ValueFunctionChain.forComparison(functions);
  }

  /**
   * @return the normalization named {@code value}, or null when there is none by that name
   */
  public static AttributeNormalization of(String value) {
    for (AttributeNormalization normalization : values()) {
      if (normalization.value.equals(value)) {
        return normalization;
      }
    }
    return null;
  }

  public String value() {
    return value;
  }

  public ValueFunctionChain chain() {
    return chain;
  }

  private static List<FunctionSpec> nameChain() {
    return List.of(
        normalizeNfkc(),
        regexReplace("[\\s\\u3000]+", ""),
        regexReplace("[\\u2010-\\u2015\\u2212]", "-"),
        new FunctionSpec("case", Map.of("mode", "lower")));
  }

  private static List<FunctionSpec> kanaChain() {
    return List.of(
        normalizeNfkc(),
        regexReplace("[\\s\\u3000]+", ""),
        regexReplace("[\\u2010-\\u2015\\u2212]", "-"),
        new FunctionSpec("kana", Map.of("to", "katakana")));
  }

  private static FunctionSpec normalizeNfkc() {
    return new FunctionSpec("normalize", Map.of("form", "NFKC"));
  }

  private static FunctionSpec regexReplace(String pattern, String replacement) {
    return new FunctionSpec(
        "regex_replace", Map.of("pattern", pattern, "replacement", replacement));
  }
}
