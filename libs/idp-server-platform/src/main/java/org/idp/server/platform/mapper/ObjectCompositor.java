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
 */
public class ObjectCompositor {

  /** Regex used to split flat keys. */
  private static final String regex = "\\.";

  /** Flat map to be transformed. */
  Map<String, Object> flatMap;

  /**
   * Constructs a new ObjectCompositor.
   *
   * @param flatMap flat key-value map to be composed
   */
  public ObjectCompositor(Map<String, Object> flatMap) {
    this.flatMap = flatMap;
  }

  /**
   * Composes a nested map structure from the flat key-value map. Supports automatic handling of
   * nested arrays and objects.
   *
   * @return nested {@code Map<String, Object>} structure
   */
  public Map<String, Object> composite() {
    Map<String, Object> result = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : flatMap.entrySet()) {
      String[] keys = entry.getKey().split(regex);
      insertValue(result, keys, entry.getValue());
    }
    return result;
  }

  /**
   * Inserts a value into the nested structure according to the provided key path. Supports both map
   * and list nodes based on key pattern (e.g., "0" for list index). Also supports special wildcard
   * key "*" for top-level expansion.
   *
   * <p>Special behavior for "*":
   *
   * <ul>
   *   <li>If the key path is exactly "*", and the value is a {@code Map}, its entries are merged
   *       into the root level.
   *   <li>Other usages of "*" (e.g. "claims.*.value") are not supported in this method and must be
   *       handled earlier.
   * </ul>
   *
   * @param root the root object to insert into
   * @param keys the path keys split by delimiter
   * @param value the value to insert
   */
  private void insertValue(Map<String, Object> root, String[] keys, Object value) {
    Object current = root;
    if (keys.length == 1 && keys[0].equals("*") && value instanceof Map) {
      //      root.putAll((Map<String, ?>) value);
    } else {
      for (int i = 0; i < keys.length; i++) {
        String key = keys[i];
        boolean isLast = i == keys.length - 1;
        boolean isIndex = key.matches("\\d+");

        if (isIndex) {
          int index = Integer.parseInt(key);
          List<Object> list;

          if (current instanceof List) {
            list = (List<Object>) current;
          } else if (current instanceof Map) {
            throw new IllegalStateException("Expected List but found Map at index: " + key);
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
              list.set(
                  index, keys[i + 1].matches("\\d+") ? new ArrayList<>() : new LinkedHashMap<>());
            }
            current = list.get(index);
          }

        } else {
          Map<String, Object> map = (Map<String, Object>) current;
          if (isLast) {
            map.put(key, value);
          } else {
            if (!map.containsKey(key)) {
              map.put(key, keys[i + 1].matches("\\d+") ? new ArrayList<>() : new LinkedHashMap<>());
            }
            current = map.get(key);
          }
        }
      }
    }
  }
}
