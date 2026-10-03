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

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;

/**
 * How a submitted value and a registered value are brought to the same form before they are
 * compared.
 *
 * <p>Applied to both sides alike, so a registered value stored in a different form from the one the
 * end-user types (full-width digits, a hyphenated phone number, a slash-separated date) still
 * matches. A value that cannot be brought to the form — a date that does not parse, an empty result
 * — normalizes to null, which never matches anything.
 */
public enum AttributeNormalization {

  /** Compared as given. */
  EXACT("exact"),

  /** Unicode NFKC, then trimmed: full-width and half-width forms compare equal. */
  NFKC("nfkc"),

  /** NFKC, then every non-digit removed: {@code 090-1234-5678} and {@code 09012345678} match. */
  DIGITS("digits"),

  /**
   * NFKC, then read as a calendar date and written as {@code yyyy-MM-dd}. Accepts {@code
   * yyyy-MM-dd}, {@code yyyy/MM/dd} (either with or without leading zeros) and {@code yyyyMMdd}.
   */
  DATE("date");

  private static final List<DateTimeFormatter> DATE_FORMATS =
      List.of(
          DateTimeFormatter.ofPattern("uuuu-M-d").withResolverStyle(ResolverStyle.STRICT),
          DateTimeFormatter.ofPattern("uuuu/M/d").withResolverStyle(ResolverStyle.STRICT),
          DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT));

  String value;

  AttributeNormalization(String value) {
    this.value = value;
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

  /**
   * @return the normalized value, or null when it cannot be normalized
   */
  public String apply(String raw) {
    if (raw == null) {
      return null;
    }
    String normalized =
        switch (this) {
          case EXACT -> raw;
          case NFKC -> nfkc(raw);
          case DIGITS -> nfkc(raw).replaceAll("[^0-9]", "");
          case DATE -> date(nfkc(raw));
        };
    return normalized == null || normalized.isEmpty() ? null : normalized;
  }

  private static String nfkc(String raw) {
    return Normalizer.normalize(raw, Normalizer.Form.NFKC).trim();
  }

  private static String date(String value) {
    for (DateTimeFormatter format : DATE_FORMATS) {
      try {
        return LocalDate.parse(value, format).toString();
      } catch (DateTimeParseException ignored) {
        // Try the next accepted form.
      }
    }
    return null;
  }
}
