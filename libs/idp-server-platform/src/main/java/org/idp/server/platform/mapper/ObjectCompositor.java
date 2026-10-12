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

package org.idp.server.platform.mapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ObjectCompositor reconstructs a nested {@code Map<String, Object>} structure from a flat map with
 * dot-separated keys, optionally supporting array indexes.
 *
 * <p>For example:
 *
 * <pre>{@code
 * Input:
 * {
 *   "claims.name": "Taro",
 *   "verification.evidence.0.type": "document"
 * }
 *
 * Output:
 * {
 *   "claims": {
 *     "name": "Taro"
 *   },
 *   "verification": {
 *     "evidence": [
 *       {
 *         "type": "document"
 *       }
 *     ]
 *   }
 * }
 * }</pre>
 *
 * <p>Keys are {@link CompositionKey}s. A key copied by {@code to: "*"} is a literal: it is not
 * split, so {@code "a.0"} stays a single map key.
 */
public class ObjectCompositor {

  /**
   * The largest list index a key may use. A list is padded with {@code null} up to the index, so
   * the index is capped (Issue #1971).
   */
  static final int MAX_LIST_INDEX = 999;

  /** Flat map to be transformed. */
  Map<CompositionKey, Object> flatMap;

  private ObjectCompositor() {}

  /**
   * Constructs a new ObjectCompositor from dot-separated paths.
   *
   * @param flatMap flat key-value map to be composed; each key is read with {@link
   *     CompositionKey#path(String)}
   */
  public ObjectCompositor(Map<String, Object> flatMap) {
    this.flatMap = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : flatMap.entrySet()) {
      this.flatMap.put(CompositionKey.path(entry.getKey()), entry.getValue());
    }
  }

  /**
   * Constructs a new ObjectCompositor from keys that are already split.
   *
   * @param flatMap flat key-value map to be composed
   */
  public static ObjectCompositor of(Map<CompositionKey, Object> flatMap) {
    ObjectCompositor compositor = new ObjectCompositor();
    compositor.flatMap = flatMap;
    return compositor;
  }

  /**
   * Composes a nested map structure from the flat key-value map. Supports automatic handling of
   * nested arrays and objects.
   *
   * @return nested {@code Map<String, Object>} structure
   */
  public Map<String, Object> composite() {
    Map<String, Object> result = new LinkedHashMap<>();
    for (Map.Entry<CompositionKey, Object> entry : flatMap.entrySet()) {
      insertValue(result, entry.getKey(), entry.getValue());
    }
    return result;
  }

  /**
   * Inserts a value into the nested structure according to the key's segments. An index segment
   * selects a list element, and a name segment selects a map entry.
   *
   * @param root the root object to insert into
   * @param key the key whose segments give the path
   * @param value the value to insert
   * @throws IllegalArgumentException if an index is larger than {@link #MAX_LIST_INDEX}
   * @throws IllegalStateException if an index meets a map
   */
  private void insertValue(Map<String, Object> root, CompositionKey key, Object value) {
    List<CompositionKey.Segment> segments = key.segments();
    Object current = root;
    for (int i = 0; i < segments.size(); i++) {
      CompositionKey.Segment segment = segments.get(i);
      boolean isLast = i == segments.size() - 1;

      if (segment.isIndex()) {
        int index = toIndex(key, segment);
        List<Object> list;

        if (current instanceof List) {
          list = (List<Object>) current;
        } else if (current instanceof Map) {
          throw new IllegalStateException(
              "Expected List but found Map at index: " + segment.value());
        } else {
          list = new ArrayList<>();
          current = list;
        }

        while (list.size() <= index) {
          list.add(null);
        }

        if (isLast) {
          list.set(index, value);
        } else {
          if (list.get(index) == null) {
            list.set(index, newContainer(segments.get(i + 1)));
          }
          current = list.get(index);
        }

      } else {
        Map<String, Object> map = (Map<String, Object>) current;
        if (isLast) {
          map.put(segment.value(), value);
        } else {
          if (!map.containsKey(segment.value())) {
            map.put(segment.value(), newContainer(segments.get(i + 1)));
          }
          current = map.get(segment.value());
        }
      }
    }
  }

  private int toIndex(CompositionKey key, CompositionKey.Segment segment) {
    String digits = segment.value();
    if (digits.length() > String.valueOf(MAX_LIST_INDEX).length()
        || Integer.parseInt(digits) > MAX_LIST_INDEX) {
      throw new IllegalArgumentException(
          String.format(
              "List index must be %d or less: %s (key: %s)", MAX_LIST_INDEX, digits, key));
    }
    return Integer.parseInt(digits);
  }

  private Object newContainer(CompositionKey.Segment next) {
    return next.isIndex() ? new ArrayList<>() : new LinkedHashMap<>();
  }
}
