/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.platform.json;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Expands a dotted key path into a nested JSON object literal.
 *
 * <p>Useful when building containment queries for JSONB columns (PostgreSQL {@code @>} / MySQL
 * {@code JSON_CONTAINS}) from a flat parameter map where keys carry the path (e.g. {@code
 * "user.sub"}). The generated literal preserves the storage layout so the containment operator can
 * leverage a GIN / JSON index instead of falling back to a {@code ->>} scan.
 *
 * <pre>
 *   buildNestedObjectJson("user.sub", "abc")  // => {"user":{"sub":"abc"}}
 *   buildNestedObjectJson("action",   "POST") // => {"action":"POST"}
 *   buildNestedObjectJson("a.b.c",    "d")    // => {"a":{"b":{"c":"d"}}}
 * </pre>
 *
 * <p>{@link #buildTypedNestedObjectJson(String, String)} additionally infers a JSON scalar type
 * (number / boolean) from the value so callers can build a type-flexible containment match (string
 * leaf OR typed leaf). This is opt-in because {@code @>} matching is type-strict, so {@code
 * {"k":3}} does not match a stored string {@code "3"} and vice versa.
 */
public final class JsonNestingBuilder {

  private static final JsonConverter JSON = JsonConverter.defaultInstance();

  // Strict numeric tokens only (no leading zeros, so "007" stays a string).
  private static final Pattern INTEGER = Pattern.compile("-?(0|[1-9]\\d*)");
  private static final Pattern DECIMAL = Pattern.compile("-?(0|[1-9]\\d*)\\.\\d+");

  private JsonNestingBuilder() {}

  /**
   * Build a JSON object string where the dotted {@code key} is expanded into nested objects and the
   * leaf carries {@code value} as a string.
   *
   * @throws IllegalArgumentException if {@code key} is null, empty, or contains an empty segment
   *     (e.g. {@code "."}, {@code "a..b"}, {@code ".a"}, {@code "a."})
   */
  public static String buildNestedObjectJson(String key, String value) {
    return buildWithLeaf(key, value);
  }

  /**
   * Build a JSON object string where the leaf carries {@code value} parsed as a JSON scalar (number
   * or boolean), or return empty when {@code value} is not a numeric / boolean token.
   *
   * <pre>
   *   buildTypedNestedObjectJson("attempts", "3")    // => Optional[{"attempts":3}]
   *   buildTypedNestedObjectJson("flag",     "true") // => Optional[{"flag":true}]
   *   buildTypedNestedObjectJson("method",   "POST") // => Optional.empty()
   *   buildTypedNestedObjectJson("zip",      "007")  // => Optional.empty() (leading zero stays string)
   * </pre>
   *
   * @throws IllegalArgumentException if {@code key} is null, empty, or contains an empty segment
   */
  public static Optional<String> buildTypedNestedObjectJson(String key, String value) {
    Object scalar = parseScalar(value);
    return scalar == null ? Optional.empty() : Optional.of(buildWithLeaf(key, scalar));
  }

  private static Object parseScalar(String value) {
    if (value == null) {
      return null;
    }
    if (value.equals("true")) {
      return Boolean.TRUE;
    }
    if (value.equals("false")) {
      return Boolean.FALSE;
    }
    if (INTEGER.matcher(value).matches()) {
      return new BigInteger(value);
    }
    if (DECIMAL.matcher(value).matches()) {
      return new BigDecimal(value);
    }
    return null;
  }

  private static String buildWithLeaf(String key, Object leaf) {
    if (key == null || key.isEmpty()) {
      throw new IllegalArgumentException("key must not be null or empty");
    }
    String[] parts = key.split("\\.", -1);
    for (String part : parts) {
      if (part.isEmpty()) {
        throw new IllegalArgumentException("key must not contain empty segments: " + key);
      }
    }
    Map<String, Object> root = new LinkedHashMap<>();
    Map<String, Object> current = root;
    for (int i = 0; i < parts.length - 1; i++) {
      Map<String, Object> next = new LinkedHashMap<>();
      current.put(parts[i], next);
      current = next;
    }
    current.put(parts[parts.length - 1], leaf);
    return JSON.write(root);
  }
}
