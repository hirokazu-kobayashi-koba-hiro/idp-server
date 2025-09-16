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

package org.idp.server.platform.date;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.idp.server.platform.exception.BadRequestException;

/**
 * Flexible parser for human-entered datetimes
 *
 * <p><strong>Supported input formats</strong> (examples):
 *
 * <ul>
 *   <li>ISO-8601 <em>local</em> datetime (no timezone):<br>
 *       {@code 2025-09-18T12:34:56}, {@code 2025-09-18T12:34:56.123456789}
 *   <li>ISO-8601 with trailing {@code Z} (UTC designator):<br>
 *       {@code 2025-09-18T12:34:56Z}, {@code 2025-09-18T12:34:56.123Z}<br>
 *       <em>Note:</em> The {@code Z} offset is parsed but the result is a {@link LocalDateTime}, so
 *       any offset/zone information is <strong>discarded</strong> (no instant conversion).
 *   <li>Space-separated datetime (microseconds normalized to 6 digits):<br>
 *       {@code 2025-09-18 12:34:56}, {@code 2025-09-18 12:34:56.1} → {@code .100000},<br>
 *       {@code 2025-09-18 12:34:56.123456789} → {@code .123456}
 * </ul>
 *
 * <p><strong>Not supported</strong>:
 *
 * <ul>
 *   <li>Date-only values (e.g., {@code 2025-09-18})
 *   <li>Offset/zone in {@code ±HH:MM} form (e.g., {@code 2025-09-18T12:34:56+09:00})
 *   <li>Non-ISO separators (e.g., {@code 2025/09/18 12:34:56})
 * </ul>
 *
 * <p><strong>Normalization rules for space-separated form</strong>:
 *
 * <ul>
 *   <li>If fractional seconds are missing, {@code .000000} is appended.
 *   <li>If fractional seconds have fewer than 6 digits, zeros are right-padded to 6 digits.
 *   <li>If fractional seconds exceed 6 digits, they are truncated to 6 digits.
 * </ul>
 *
 * <p><strong>Important</strong>:
 *
 * <ul>
 *   <li>This parser returns {@link LocalDateTime}. It does <em>not</em> apply timezone/offset
 *       conversion even if input includes {@code Z}. If you need instant/offset-aware semantics,
 *       parse into {@code OffsetDateTime}/{@code Instant} instead and convert explicitly.
 *   <li>On invalid input, a {@link BadRequestException} is thrown with the underlying parse error
 *       message.
 * </ul>
 *
 * <p><strong>Examples</strong>:
 *
 * <pre>{@code
 * LocalDateTimeParser.parse("2025-09-18T12:34:56");          // OK
 * LocalDateTimeParser.parse("2025-09-18T12:34:56.123Z");     // OK (Z ignored in result)
 * LocalDateTimeParser.parse("2025-09-18 12:34:56");          // OK → .000000 appended
 * LocalDateTimeParser.parse("2025-09-18 12:34:56.1");        // OK → .100000
 * LocalDateTimeParser.parse("2025-09-18 12:34:56.123456789");// OK → .123456
 * LocalDateTimeParser.parse("2025-09-18");                   // throws BadRequestException
 * LocalDateTimeParser.parse("2025-09-18T12:34:56+09:00");    // throws BadRequestException
 * }</pre>
 *
 * <p><strong>Thread-safety</strong>: stateless and thread-safe.
 */
public class LocalDateTimeParser {

  /**
   * Parses a datetime string into {@link LocalDateTime}.
   *
   * <p>Dispatch logic:
   *
   * <ol>
   *   <li>If the input contains both {@code 'T'} and {@code 'Z'}, parse with {@link
   *       DateTimeFormatter#ISO_DATE_TIME} (offset info is ignored in the result).
   *   <li>Else if the input contains {@code 'T'}, parse with the default ISO local formatter
   *       ({@code LocalDateTime.parse(text)}).
   *   <li>Else parse as space-separated {@code yyyy-MM-dd HH:mm:ss.SSSSSS} after normalizing
   *       fractional seconds to exactly 6 digits.
   * </ol>
   *
   * @param date input datetime string (see class-level Javadoc for supported formats)
   * @return parsed {@link LocalDateTime}
   * @throws BadRequestException if the input cannot be parsed
   */
  public static LocalDateTime parse(String date) {
    try {

      if (date.contains("T") && date.contains("Z")) {
        // e.g. 2025-09-18T12:34:56Z (offset parsed, then discarded because result is LocalDateTime)
        return LocalDateTime.parse(date, DateTimeFormatter.ISO_DATE_TIME);
      }

      if (date.contains("T")) {
        // e.g. 2025-09-18T12:34:56(.nanos), timezone-less ISO local datetime
        return LocalDateTime.parse(date);
      }

      // e.g. 2025-09-18 12:34:56(.fraction) → normalized to microseconds (6 digits)
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");
      return LocalDateTime.parse(normalizeDateTime(date), formatter);
    } catch (DateTimeParseException e) {
      throw new BadRequestException(e.getMessage());
    }
  }

  /**
   * Normalizes the fractional seconds of a space-separated datetime to exactly 6 digits.
   *
   * <p>Behavior:
   *
   * <ul>
   *   <li>No fraction → append {@code .000000}
   *   <li>1–5 digits → right-pad with zeros to 6 digits
   *   <li>&gt;6 digits → truncate to the first 6 digits
   * </ul>
   *
   * <p><strong>Input expectations</strong>: the caller is responsible for ensuring the base format
   * {@code yyyy-MM-dd HH:mm:ss[.fraction]} prior to normalization.
   *
   * @param dateTimeStr space-separated datetime string
   * @return normalized string with a 6-digit fractional second field
   */
  private static String normalizeDateTime(String dateTimeStr) {
    if (!dateTimeStr.contains(".")) {
      return dateTimeStr + ".000000"; // No fraction present, add six zeroes
    }

    String[] parts = dateTimeStr.split("\\.");
    String fraction = parts[1];

    // Ensure fraction has exactly 6 digits
    if (fraction.length() < 6) {
      fraction = String.format("%-6s", fraction).replace(' ', '0'); // Pad with zeros
    } else if (fraction.length() > 6) {
      fraction = fraction.substring(0, 6); // Trim to 6 digits
    }

    return parts[0] + "." + fraction;
  }
}
