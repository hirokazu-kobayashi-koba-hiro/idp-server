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

import java.util.LinkedHashMap;
import java.util.Map;

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
 */
public final class JsonNestingBuilder {

  private static final JsonConverter JSON = JsonConverter.defaultInstance();

  private JsonNestingBuilder() {}

  /**
   * Build a JSON object string where the dotted {@code key} is expanded into nested objects and the
   * leaf carries {@code value}.
   *
   * @throws IllegalArgumentException if {@code key} is null, empty, or contains an empty segment
   *     (e.g. {@code "."}, {@code "a..b"}, {@code ".a"}, {@code "a."})
   */
  public static String buildNestedObjectJson(String key, String value) {
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
    current.put(parts[parts.length - 1], value);
    return JSON.write(root);
  }
}
