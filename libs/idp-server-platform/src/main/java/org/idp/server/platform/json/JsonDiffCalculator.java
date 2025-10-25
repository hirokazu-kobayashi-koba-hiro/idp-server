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

package org.idp.server.platform.json;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * {@code JsonDiffCalculator} is a utility class to calculate the deep structural differences
 * between two JSON objects represented by {@link JsonNodeWrapper}.
 *
 * <p>This class supports recursive comparison of nested JSON structures including objects and
 * arrays. Differences are returned as a flat {@code Map<String, Object>} with dot-separated keys
 * indicating the path to the changed value.
 *
 * <p>Use cases include configuration diffing, dry-run audits, change tracking, and version
 * comparisons.
 *
 * <p><b>Note:</b> For arrays, the comparison is shallow. If any element differs, the entire array
 * is returned as changed.
 */
public class JsonDiffCalculator {

  /**
   * Calculates the deep differences between two JSON objects.
   *
   * @param before the original JSON structure wrapped in {@link JsonNodeWrapper}
   * @param after the modified JSON structure wrapped in {@link JsonNodeWrapper}
   * @return a flat {@code Map<String, Object>} representing changed fields. The keys represent the
   *     full JSON path using dot notation.
   */
  public static Map<String, Object> deepDiff(JsonNodeWrapper before, JsonNodeWrapper after) {
    Map<String, Object> diff = new HashMap<>();
    diffRecursive("", before, after, diff);
    return diff;
  }

  /**
   * Converts a JSON array to a List, preserving primitive types and recursively converting nested
   * structures.
   *
   * @param wrapper the JSON array wrapper
   * @return List of converted elements
   */
  private static List<Object> convertArrayToList(JsonNodeWrapper wrapper) {
    List<Object> list = new ArrayList<>();
    wrapper
        .elements()
        .forEach(
            element -> {
              switch (element.nodeType()) {
                case OBJECT:
                  list.add(element.toMap());
                  break;
                case ARRAY:
                  list.add(convertArrayToList(element));
                  break;
                case STRING:
                  list.add(element.asText());
                  break;
                case INT:
                  list.add(element.asInt());
                  break;
                case BOOLEAN:
                case LONG:
                case DOUBLE:
                case NULL:
                default:
                  list.add(element.node());
                  break;
              }
            });
    return list;
  }

  /**
   * Recursively compares JSON nodes and accumulates differences in the given {@code diff} map.
   *
   * @param path the current JSON path (dot notation)
   * @param before the original value at the path
   * @param after the new value at the path
   * @param diff accumulator map for differences
   */
  private static void diffRecursive(
      String path, JsonNodeWrapper before, JsonNodeWrapper after, Map<String, Object> diff) {
    if (before == null || !before.exists()) {
      // Added field
      diff.put(path, after.toMap());
      return;
    }

    if (before.nodeType() != after.nodeType()) {
      if (after.isString()) {
        diff.put(path, after.asText());
      } else {
        diff.put(path, after.toMap());
      }
      return;
    }

    switch (after.nodeType()) {
      case OBJECT:
        Iterator<String> fields = after.fieldNames();
        while (fields.hasNext()) {
          String field = fields.next();
          String newPath = path.isEmpty() ? field : path + "." + field;
          JsonNodeWrapper beforeChild =
              before.contains(field) ? before.getValueAsJsonNode(field) : JsonNodeWrapper.empty();
          JsonNodeWrapper afterChild = after.getValueAsJsonNode(field);
          diffRecursive(newPath, beforeChild, afterChild, diff);
        }
        break;
      case ARRAY:
        // Shallow comparison for arrays
        if (!before.node().equals(after.node())) {
          // Convert array to List, recursively converting nested structures
          List<Object> afterList = convertArrayToList(after);
          diff.put(path, afterList);
        }
        break;
      default:
        if (!Objects.equals(before.node(), after.node())) {
          diff.put(path, after.node());
        }
        break;
    }
  }
}
