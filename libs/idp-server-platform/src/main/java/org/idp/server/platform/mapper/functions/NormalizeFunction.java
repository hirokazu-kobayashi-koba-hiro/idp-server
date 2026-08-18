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

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

/**
 * Unicode normalization function (UAX #15).
 *
 * <p>Lets a mapping rule reconcile the same value arriving in different notations. The motivating
 * case is a value received from two external sources, one in halfwidth katakana and one in
 * fullwidth: they are not equal as strings and cannot be matched without normalizing first.
 *
 * <p>The existing string functions cannot express this. A voiced halfwidth katakana is two code
 * points ({@code ｶ} U+FF76 + {@code ﾞ} U+FF9E) where its fullwidth form is one ({@code ガ} U+30AC),
 * so a chain of {@code replace} rules is order-dependent and needs more than eighty entries to
 * cover the syllabary.
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * {
 *   "name": "normalize",
 *   "args": {
 *     "form": "NFKC"   // Optional, one of NFC / NFD / NFKC / NFKD, default: NFKC.
 *                      // Case-insensitive. An unknown form raises IllegalArgumentException
 *                      // rather than passing the value through unchanged.
 *   }
 * }
 * }</pre>
 *
 * <h2>Forms</h2>
 *
 * <ul>
 *   <li><b>NFC</b> — canonical decomposition, then canonical composition. Combining marks are
 *       folded into precomposed characters. Notation is preserved.
 *   <li><b>NFD</b> — canonical decomposition only.
 *   <li><b>NFKC</b> — compatibility decomposition, then canonical composition. Folds notational
 *       variants together, which is what makes matching possible.
 *   <li><b>NFKD</b> — compatibility decomposition only.
 * </ul>
 *
 * <h2>Examples</h2>
 *
 * <pre>{@code
 * Input: "ﾔﾏﾀﾞ ﾀﾛｳ"   NFKC: "ヤマダ タロウ"   NFC: "ﾔﾏﾀﾞ ﾀﾛｳ" (unchanged)
 * Input: "ｶﾞ"          NFKC: "ガ" (1 code point)   NFKD: "カ" + U+3099 (2 code points)
 * Input: "Ａｂｃ１２３"  NFKC: "Abc123"
 * Input: "①"           NFKC: "1"
 * Input: "㈱"           NFKC: "(株)"
 * }</pre>
 *
 * <p>The last three are why {@code form} is an argument rather than a constant. NFKC folds
 * notation, and that is not always wanted: a display name normalized with NFKC loses {@code ㈱} and
 * fullwidth alphanumerics. Use NFC when the value only needs a canonical representation and its
 * notation has to survive.
 *
 * <h2>Not for identity matching</h2>
 *
 * <p>Folding notation together means different inputs produce the same output, so this must not be
 * applied to a field that decides whether two values are the same principal — {@code
 * identity_match_field} in {@code external-api-authentication} compares such a value with {@code
 * equals}. An attacker who can register {@code "Ａdmin@example.com"} (fullwidth U+FF21) at the
 * external provider would match a stored {@code "Admin@example.com"}. Some foldings render
 * identically to their target (U+212A KELVIN SIGN becomes {@code K}), so review cannot catch them.
 *
 * <p>Choosing NFC does not avoid this. U+212A and U+212B have canonical, not compatibility,
 * decompositions, so every form folds them; only the compatibility cases above are specific to
 * NFKC/NFKD. No form is safe for identity matching — use the raw value.
 *
 * <h2>Scope</h2>
 *
 * <p>Unicode normalization only. Whitespace removal, case folding and trimming are {@code
 * regex_replace}, {@code case} and {@code trim} — compose them in a chain:
 *
 * <pre>{@code
 * "functions": [
 *   { "name": "normalize", "args": { "form": "NFKC" } },
 *   { "name": "regex_replace", "args": { "pattern": "[\\s\\u3000]+", "replacement": "" } }
 * ]
 * }</pre>
 *
 * <p>Hiragana and katakana are distinct characters, not notational variants, so no form converts
 * between them.
 *
 * <p>Arrays are handled by {@code map}. Its argument key is {@code function_args}: nesting {@code
 * args} instead is not an error, it silently leaves this function with no arguments and applies the
 * default NFKC.
 *
 * <pre>{@code
 * { "name": "map", "args": { "function": "normalize", "function_args": { "form": "NFC" } } }
 * }</pre>
 *
 * @see <a href="https://www.unicode.org/reports/tr15/">UAX #15: Unicode Normalization Forms</a>
 */
public class NormalizeFunction implements ValueFunction {

  private static final Normalizer.Form DEFAULT_FORM = Normalizer.Form.NFKC;

  @Override
  public String name() {
    return "normalize";
  }

  @Override
  public Object apply(Object input, Map<String, Object> args) {
    if (input == null) {
      return null;
    }

    String value = input.toString();
    if (value.isEmpty()) {
      return value;
    }

    return Normalizer.normalize(value, resolveForm(getStringArg(args, "form")));
  }

  /**
   * Resolves the requested normalization form.
   *
   * <p>An absent or blank {@code form} means "unspecified" and falls back to the default. An
   * unknown one is a configuration error: silently returning the value unnormalized would let a
   * comparison that depends on this function pass on unnormalized input.
   */
  private static Normalizer.Form resolveForm(String form) {
    if (form == null || form.isBlank()) {
      return DEFAULT_FORM;
    }
    try {
      return Normalizer.Form.valueOf(form.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "normalize: invalid form '" + form + "' (use NFC, NFD, NFKC or NFKD)", e);
    }
  }

  private static String getStringArg(Map<String, Object> args, String key) {
    if (args == null) {
      return null;
    }
    Object value = args.get(key);
    return value != null ? value.toString() : null;
  }
}
