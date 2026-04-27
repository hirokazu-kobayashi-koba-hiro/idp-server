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
 * {@code AppendFunction} appends a new element to a collection.
 *
 * <p>This function takes a collection input and returns a new collection with the specified value
 * appended at the end. If the input is null, a new single-element list is returned.
 *
 * <p>Arguments:
 *
 * <ul>
 *   <li><b>value</b>: The value to append to the collection (required)
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Append a string element
 * {"name": "append", "args": {
 *   "value": "new_element"
 * }}
 *
 * // Append a numeric element
 * {"name": "append", "args": {
 *   "value": 42
 * }}
 * }</pre>
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>Input: ["a", "b"], value: "c" -> ["a", "b", "c"]
 *   <li>Input: null, value: "first" -> ["first"]
 *   <li>Input: "single", value: "added" -> ["single", "added"]
 * </ul>
 */
public class AppendFunction implements ValueFunction {

  @Override
  public Object apply(Object input, Map<String, Object> args) {
    if (args == null) {
      throw new IllegalArgumentException("append: 'value' argument is required");
    }

    Object value = args.get("value");

    List<Object> result;
    if (input == null) {
      result = new ArrayList<>();
    } else {
      Collection<?> collection = toCollection(input);
      result = new ArrayList<>(collection);
    }

    result.add(value);
    return result;
  }

  @Override
  public String name() {
    return "append";
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
      List<Boolean> list = new ArrayList<>();
      for (boolean b : arr) {
        list.add(b);
      }
      return list;
    }

    return Collections.singletonList(input);
  }
}
