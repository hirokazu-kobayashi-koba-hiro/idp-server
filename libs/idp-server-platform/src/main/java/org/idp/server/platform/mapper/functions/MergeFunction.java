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

import java.util.*;
import java.util.stream.Collectors;

/**
 * {@code MergeFunction} merges two collections into one.
 *
 * <p>This function takes a collection input and merges it with another collection specified in the
 * arguments. It supports optional deduplication by value equality or by a specific key for object
 * arrays.
 *
 * <p>Arguments:
 *
 * <ul>
 *   <li><b>source</b>: The collection to merge with (required). Can be a List or array.
 *   <li><b>distinct</b>: Whether to remove duplicates after merging (optional, defaults to false)
 *   <li><b>key</b>: For object arrays, the key to use for deduplication (optional). When specified,
 *       objects with duplicate key values are deduplicated keeping the last occurrence.
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Simple merge
 * {"name": "merge", "args": {
 *   "source": ["c", "d"]
 * }}
 *
 * // Merge with deduplication
 * {"name": "merge", "args": {
 *   "source": ["b", "c"],
 *   "distinct": true
 * }}
 *
 * // Merge object arrays with key-based deduplication
 * {"name": "merge", "args": {
 *   "source": [{"account_no": "123", "balance": 200}],
 *   "key": "account_no"
 * }}
 * }</pre>
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>Input: ["a", "b"], source: ["c", "d"] -> ["a", "b", "c", "d"]
 *   <li>Input: ["a", "b"], source: ["b", "c"], distinct: true -> ["a", "b", "c"]
 *   <li>Input: [{account_no: "123"}], source: [{account_no: "123", balance: 200}], key:
 *       "account_no" -> [{account_no: "123", balance: 200}]
 * </ul>
 */
public class MergeFunction implements ValueFunction {

  @Override
  public Object apply(Object input, Map<String, Object> args) {
    if (args == null) {
      throw new IllegalArgumentException("merge: 'source' argument is required");
    }

    Object sourceArg = args.get("source");
    if (sourceArg == null) {
      throw new IllegalArgumentException("merge: 'source' argument is required");
    }

    boolean distinct = getBooleanArg(args, "distinct", false);
    String key = getStringArg(args, "key");

    List<Object> baseList;
    if (input == null) {
      baseList = new ArrayList<>();
    } else {
      baseList = new ArrayList<>(toCollection(input));
    }

    Collection<?> sourceCollection = toCollection(sourceArg);
    List<Object> merged = new ArrayList<>(baseList);
    merged.addAll(sourceCollection);

    if (key != null && !key.isEmpty()) {
      return deduplicateByKey(merged, key);
    }

    if (distinct) {
      return deduplicateByEquality(merged);
    }

    return merged;
  }

  @Override
  public String name() {
    return "merge";
  }

  @SuppressWarnings("unchecked")
  private List<Object> deduplicateByKey(List<Object> list, String key) {
    LinkedHashMap<Object, Object> seen = new LinkedHashMap<>();
    for (Object element : list) {
      if (element instanceof Map) {
        Object keyValue = ((Map<String, Object>) element).get(key);
        seen.put(keyValue, element);
      } else {
        seen.put(element, element);
      }
    }
    return new ArrayList<>(seen.values());
  }

  private List<Object> deduplicateByEquality(List<Object> list) {
    LinkedHashSet<Object> seen = new LinkedHashSet<>(list);
    return new ArrayList<>(seen);
  }

  private Collection<?> toCollection(Object input) {
    if (input instanceof Collection<?>) {
      return (Collection<?>) input;
    }

    if (input instanceof Object[]) {
      return Arrays.asList((Object[]) input);
    }

    if (input instanceof int[]) {
      return Arrays.stream((int[]) input).boxed().collect(Collectors.toList());
    }

    if (input instanceof long[]) {
      return Arrays.stream((long[]) input).boxed().collect(Collectors.toList());
    }

    if (input instanceof double[]) {
      return Arrays.stream((double[]) input).boxed().collect(Collectors.toList());
    }

    if (input instanceof boolean[]) {
      boolean[] arr = (boolean[]) input;
      List<Boolean> boolList = new ArrayList<>();
      for (boolean b : arr) {
        boolList.add(b);
      }
      return boolList;
    }

    return Collections.singletonList(input);
  }

  private static String getStringArg(Map<String, Object> args, String key) {
    if (args == null) return null;
    Object value = args.get(key);
    return value != null ? value.toString() : null;
  }

  private static boolean getBooleanArg(Map<String, Object> args, String key, boolean defaultValue) {
    if (args == null) return defaultValue;
    Object value = args.get(key);
    if (value instanceof Boolean) {
      return (Boolean) value;
    }
    if (value instanceof String) {
      return Boolean.parseBoolean((String) value);
    }
    return defaultValue;
  }
}
