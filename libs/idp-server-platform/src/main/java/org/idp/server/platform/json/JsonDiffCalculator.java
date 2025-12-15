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
    if (before == null || !before.existsWithValue()) {
      // Added field: {before: null, after: <value>}
      Map<String, Object> addedDiff = new HashMap<>();
      addedDiff.put("before", null);
      addedDiff.put("after", toSerializableValue(after));
      diff.put(path, addedDiff);
      return;
    }

    if (before.nodeType() != after.nodeType()) {
      // Type changed: {before: <old>, after: <new>}
      Map<String, Object> changedDiff = new HashMap<>();
      changedDiff.put("before", toSerializableValue(before));
      changedDiff.put("after", toSerializableValue(after));
      diff.put(path, changedDiff);
      return;
    }

    switch (after.nodeType()) {
      case OBJECT:
        // Check fields in 'after' (added or changed)
        Iterator<String> afterFields = after.fieldNames();
        while (afterFields.hasNext()) {
          String field = afterFields.next();
          String newPath = path.isEmpty() ? field : path + "." + field;
          JsonNodeWrapper beforeChild =
              before.contains(field) ? before.getValueAsJsonNode(field) : JsonNodeWrapper.empty();
          JsonNodeWrapper afterChild = after.getValueAsJsonNode(field);
          diffRecursive(newPath, beforeChild, afterChild, diff);
        }
        // Check fields in 'before' that were removed in 'after'
        Iterator<String> beforeFields = before.fieldNames();
        while (beforeFields.hasNext()) {
          String field = beforeFields.next();
          if (!after.contains(field)) {
            String newPath = path.isEmpty() ? field : path + "." + field;
            JsonNodeWrapper beforeChild = before.getValueAsJsonNode(field);
            // Removed field: {before: <value>, after: null}
            Map<String, Object> removedDiff = new HashMap<>();
            removedDiff.put("before", toSerializableValue(beforeChild));
            removedDiff.put("after", null);
            diff.put(newPath, removedDiff);
          }
        }
        break;
      case ARRAY:
        // Shallow comparison for arrays
        if (!before.node().equals(after.node())) {
          // Array changed: {before: <old>, after: <new>}
          Map<String, Object> arrayDiff = new HashMap<>();
          arrayDiff.put("before", convertArrayToList(before));
          arrayDiff.put("after", convertArrayToList(after));
          diff.put(path, arrayDiff);
        }
        break;
      default:
        if (!Objects.equals(before.node(), after.node())) {
          // Primitive changed: {before: <old>, after: <new>}
          Map<String, Object> primitiveDiff = new HashMap<>();
          primitiveDiff.put("before", toSerializableValue(before));
          primitiveDiff.put("after", toSerializableValue(after));
          diff.put(path, primitiveDiff);
        }
        break;
    }
  }

  /**
   * Converts a JsonNodeWrapper to a serializable value.
   *
   * @param wrapper the JSON node wrapper
   * @return a serializable representation of the value
   */
  private static Object toSerializableValue(JsonNodeWrapper wrapper) {
    if (wrapper == null || !wrapper.existsWithValue()) {
      return null;
    }
    switch (wrapper.nodeType()) {
      case OBJECT:
        return wrapper.toMap();
      case ARRAY:
        return convertArrayToList(wrapper);
      case STRING:
        return wrapper.asText();
      case INT:
        return wrapper.asInt();
      case BOOLEAN:
      case LONG:
      case DOUBLE:
      case NULL:
      default:
        return wrapper.node();
    }
  }
}
