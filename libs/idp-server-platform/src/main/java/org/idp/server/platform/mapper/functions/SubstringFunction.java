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

import java.util.Map;

/**
 * {@code SubstringFunction} extracts a portion of a string based on start and end positions.
 *
 * <p>This function provides flexible string extraction with support for negative indices, automatic
 * bounds checking, and safe handling of edge cases.
 *
 * <p>Arguments:
 *
 * <ul>
 *   <li><b>start</b>: Starting position (inclusive, 0-based). Negative values count from end.
 *   <li><b>end</b>: Ending position (exclusive). Negative values count from end. Optional - if not
 *       specified, extracts to end of string.
 *   <li><b>length</b>: Alternative to 'end' - specifies number of characters to extract.
 * </ul>
 *
 * <p>Index handling:
 *
 * <ul>
 *   <li>Positive indices: 0-based from start of string
 *   <li>Negative indices: count backwards from end (-1 = last character)
 *   <li>Out-of-bounds indices are automatically clamped to valid range
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Extract from position 2 to 5
 * {"name": "substring", "args": {"start": 2, "end": 5}}
 *
 * // Extract 3 characters from position 1
 * {"name": "substring", "args": {"start": 1, "length": 3}}
 *
 * // Extract last 3 characters
 * {"name": "substring", "args": {"start": -3}}
 *
 * // Extract from 2nd to 2nd-last character
 * {"name": "substring", "args": {"start": 1, "end": -1}}
 * }</pre>
 *
 * <p>Input: "hello world"
 *
 * <ul>
 *   <li>start=0, end=5 → "hello"
 *   <li>start=6 → "world"
 *   <li>start=-5 → "world"
 *   <li>start=1, length=3 → "ell"
 * </ul>
 */
public class SubstringFunction implements ValueFunction {

  @Override
  public Object apply(Object input, Map<String, Object> args) {
    if (input == null) {
      return null;
    }

    String str = input.toString();
    if (str.isEmpty()) {
      return str;
    }

    int length = str.length();
    int start = getIntArg(args, "start", 0);
    Integer end = null;
    Integer lengthArg = null;

    // Check if 'end' or 'length' argument is provided
    if (args != null) {
      if (args.containsKey("end")) {
        end = getIntArg(args, "end", length);
      }
      if (args.containsKey("length")) {
        lengthArg = getIntArg(args, "length", 0);
      }
    }

    // Convert negative indices to positive
    if (start < 0) {
      start = Math.max(0, length + start);
    } else {
      start = Math.min(start, length);
    }

    // Calculate end position
    int endPos;
    if (lengthArg != null) {
      // Use length argument
      endPos = Math.min(start + Math.max(0, lengthArg), length);
    } else if (end != null) {
      // Use end argument
      if (end < 0) {
        endPos = Math.max(start, length + end);
      } else {
        endPos = Math.min(end, length);
      }
    } else {
      // No end specified, extract to end of string
      endPos = length;
    }

    // Ensure start <= end
    if (start > endPos) {
      return "";
    }

    return str.substring(start, endPos);
  }

  @Override
  public String name() {
    return "substring";
  }

  /**
   * Helper method to extract integer argument with default value.
   *
   * @param args argument map
   * @param key argument key
   * @param defaultValue default value if key not found or invalid
   * @return parsed integer value
   */
  private static int getIntArg(Map<String, Object> args, String key, int defaultValue) {
    if (args == null) return defaultValue;
    Object value = args.get(key);
    if (value == null) return defaultValue;
    if (value instanceof Number) return ((Number) value).intValue();
    try {
      return Integer.parseInt(value.toString());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }
}
