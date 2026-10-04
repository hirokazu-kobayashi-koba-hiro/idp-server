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

import java.util.Locale;
import java.util.Map;

/**
 * Converts between hiragana and katakana.
 *
 * <p>Lets a mapping rule — or a comparison built from these functions — treat a reading written in
 * hiragana and the same reading in katakana as one value: {@code やまだ} and {@code ヤマダ}. Unicode
 * normalization cannot do this; the two scripts are distinct characters, not notational variants of
 * each other, so no normalization form maps one to the other (see {@link NormalizeFunction}).
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * {
 *   "name": "kana",
 *   "args": {
 *     "to": "katakana"   // Optional, "katakana" or "hiragana", default: katakana.
 *                        // An unknown value raises IllegalArgumentException rather than
 *                        // passing the input through unchanged.
 *   }
 * }
 * }</pre>
 *
 * <h2>What is converted</h2>
 *
 * <p>The hiragana letters U+3041–U+3096 and the iteration marks ゝ ゞ (U+309D, U+309E) map to the
 * katakana U+30A1–U+30F6 and ヽ ヾ (U+30FD, U+30FE), and back. Everything else is left as it is,
 * including:
 *
 * <ul>
 *   <li>the prolonged sound mark ー, which both scripts share
 *   <li>katakana with no hiragana counterpart (ヷ ヸ ヹ ヺ), when converting to hiragana
 *   <li>halfwidth katakana. Fold it to fullwidth with {@code normalize} (NFKC) first:
 * </ul>
 *
 * <pre>{@code
 * "functions": [
 *   { "name": "normalize", "args": { "form": "NFKC" } },
 *   { "name": "kana", "args": { "to": "katakana" } }
 * ]
 * }</pre>
 *
 * <h2>Examples</h2>
 *
 * <pre>{@code
 * Input: "やまだ たろう"   katakana: "ヤマダ タロウ"
 * Input: "ジョーンズ"      hiragana: "じょーんず"   (ー is kept)
 * Input: "ﾔﾏﾀﾞ"           katakana: "ﾔﾏﾀﾞ"         (halfwidth: normalize first)
 * }</pre>
 *
 * <h2>Not for identity matching</h2>
 *
 * <p>Like {@link NormalizeFunction}, this makes different inputs equal, so it must not be applied
 * to a value that decides which principal a request is about.
 */
public class KanaFunction implements ValueFunction {

  private static final int HIRAGANA_START = 0x3041;
  private static final int HIRAGANA_END = 0x3096;
  private static final int KATAKANA_START = 0x30A1;
  private static final int KATAKANA_END = 0x30F6;
  private static final int OFFSET = KATAKANA_START - HIRAGANA_START;

  private static final int HIRAGANA_ITERATION = 0x309D;
  private static final int HIRAGANA_VOICED_ITERATION = 0x309E;
  private static final int KATAKANA_ITERATION = 0x30FD;
  private static final int KATAKANA_VOICED_ITERATION = 0x30FE;

  @Override
  public String name() {
    return "kana";
  }

  @Override
  public Object apply(Object input, Map<String, Object> args) {
    // Read before the input is looked at, so a wrong target is reported whatever the input is.
    boolean toKatakana = resolveToKatakana(args);
    if (input == null) {
      return null;
    }
    String value = input.toString();
    if (value.isEmpty()) {
      return value;
    }
    StringBuilder converted = new StringBuilder(value.length());
    value
        .codePoints()
        .forEach(
            codePoint ->
                converted.appendCodePoint(
                    toKatakana ? toKatakana(codePoint) : toHiragana(codePoint)));
    return converted.toString();
  }

  private static int toKatakana(int codePoint) {
    if (codePoint >= HIRAGANA_START && codePoint <= HIRAGANA_END) {
      return codePoint + OFFSET;
    }
    if (codePoint == HIRAGANA_ITERATION) {
      return KATAKANA_ITERATION;
    }
    if (codePoint == HIRAGANA_VOICED_ITERATION) {
      return KATAKANA_VOICED_ITERATION;
    }
    return codePoint;
  }

  private static int toHiragana(int codePoint) {
    if (codePoint >= KATAKANA_START && codePoint <= KATAKANA_END) {
      return codePoint - OFFSET;
    }
    if (codePoint == KATAKANA_ITERATION) {
      return HIRAGANA_ITERATION;
    }
    if (codePoint == KATAKANA_VOICED_ITERATION) {
      return HIRAGANA_VOICED_ITERATION;
    }
    return codePoint;
  }

  private static boolean resolveToKatakana(Map<String, Object> args) {
    Object to = args != null ? args.get("to") : null;
    if (to == null || to.toString().isBlank()) {
      return true;
    }
    return switch (to.toString().toLowerCase(Locale.ROOT)) {
      case "katakana" -> true;
      case "hiragana" -> false;
      default ->
          throw new IllegalArgumentException(
              "kana: invalid to '" + to + "' (use katakana or hiragana)");
    };
  }
}
