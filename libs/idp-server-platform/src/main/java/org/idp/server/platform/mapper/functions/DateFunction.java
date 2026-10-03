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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Map;

/**
 * Reads a calendar date written in one of the common notations and writes it in one format.
 *
 * <p>The same birthdate arrives as {@code 1990-04-01}, {@code 1990/4/1}, {@code 19900401} or {@code
 * 1990年4月1日} depending on who typed it or which system returned it. This brings them to one
 * notation so they can be compared or passed on.
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * {
 *   "name": "date",
 *   "args": {
 *     "format": "uuuu-MM-dd"   // Optional, a java.time pattern for the output, default: uuuu-MM-dd.
 *                              // An invalid pattern raises IllegalArgumentException.
 *   }
 * }
 * }</pre>
 *
 * <h2>Accepted notations</h2>
 *
 * <p>The input is first folded with NFKC and trimmed, so fullwidth digits and separators are read
 * as their ASCII forms. Then, with or without leading zeros in month and day:
 *
 * <ul>
 *   <li>{@code 1990-04-01}, {@code 1990/4/1}, {@code 1990.4.1}
 *   <li>{@code 19900401} (eight digits, zero-padded)
 *   <li>{@code 1990年4月1日}
 * </ul>
 *
 * <p>Dates are resolved strictly: {@code 1990-02-30} is not a date. Anything that cannot be read —
 * an impossible date, another notation, the Japanese era ({@code H2.4.1}) — yields {@code null}
 * rather than the input unchanged, so a comparison built on this never mistakes an unread value for
 * a read one.
 *
 * <h2>Examples</h2>
 *
 * <pre>{@code
 * Input: "１９９０／０４／０１"   Output: "1990-04-01"
 * Input: "1990年4月1日"           Output: "1990-04-01"
 * Input: "19900401"               format "uuuu/MM/dd": "1990/04/01"
 * Input: "H2.4.1"                 Output: null
 * }</pre>
 */
public class DateFunction implements ValueFunction {

  private static final String DEFAULT_FORMAT = "uuuu-MM-dd";

  private static final List<DateTimeFormatter> ACCEPTED =
      List.of(
          strict("uuuu-M-d"),
          strict("uuuu/M/d"),
          strict("uuuu.M.d"),
          strict("uuuuMMdd"),
          strict("uuuu年M月d日"));

  @Override
  public String name() {
    return "date";
  }

  @Override
  public Object apply(Object input, Map<String, Object> args) {
    DateTimeFormatter output = resolveFormat(args);
    if (input == null) {
      return null;
    }
    String value = Normalizer.normalize(input.toString(), Normalizer.Form.NFKC).trim();
    for (DateTimeFormatter accepted : ACCEPTED) {
      try {
        return LocalDate.parse(value, accepted).format(output);
      } catch (DateTimeParseException ignored) {
        // Try the next notation.
      }
    }
    return null;
  }

  private static DateTimeFormatter resolveFormat(Map<String, Object> args) {
    Object format = args != null ? args.get("format") : null;
    if (format == null || format.toString().isBlank()) {
      return DateTimeFormatter.ofPattern(DEFAULT_FORMAT);
    }
    try {
      return DateTimeFormatter.ofPattern(format.toString());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("date: invalid format '" + format + "'", e);
    }
  }

  private static DateTimeFormatter strict(String pattern) {
    return DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT);
  }
}
