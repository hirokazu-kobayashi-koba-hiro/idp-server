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
 * {@code RemoveFunction} removes all elements equal to a given value from a collection.
 *
 * <p>This function is the counterpart of {@link AppendFunction}: where {@code append} adds an
 * element, {@code remove} strips every element that {@link Objects#equals(Object, Object) equals}
 * the supplied {@code value}. Combined with dynamic args resolution (a {@code value} of {@code
 * "$.request_body.service_id"} is resolved against the source data before the function runs), it
 * expresses the "deregister a request-specified value from the user's existing array" use case
 * purely in mapping configuration.
 *
 * <p>Arguments:
 *
 * <ul>
 *   <li><b>value</b>: The value to remove from the collection (required). All matching elements are
 *       removed.
 *   <li><b>field</b>: When set, compare {@code element[field]} against {@code value} instead of the
 *       whole element (optional). Symmetric with {@link FilterFunction}'s {@code field} arg, this
 *       lets object arrays be deregistered by key.
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Deregister a service id supplied in the request from the user's subscriptions (scalar array)
 * {
 *   "from": "$.user.custom_properties.subscriptions",
 *   "to": "subscriptions",
 *   "functions": [
 *     { "name": "remove", "args": { "value": "$.request_body.service_id" } }
 *   ]
 * }
 *
 * // Deregister an account object by its account_no key (object array)
 * {
 *   "from": "$.user.custom_properties.accounts",
 *   "to": "accounts",
 *   "functions": [
 *     { "name": "remove", "args": { "field": "account_no", "value": "$.request_body.account_no" } }
 *   ]
 * }
 * }</pre>
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>Input: ["a", "b", "c"], value: "b" -> ["a", "c"]
 *   <li>Input: ["a", "b", "b"], value: "b" -> ["a"] (removes every occurrence)
 *   <li>Input: ["a"], value: "x" -> ["a"] (no match, unchanged)
 *   <li>Input: null, value: "x" -> null (no collection to operate on)
 *   <li>Input: [{id:"1"},{id:"2"}], field: "id", value: "2" -> [{id:"1"}] (key-based removal)
 * </ul>
 *
 * <p>Null handling:
 *
 * <ul>
 *   <li>{@code input == null} returns {@code null} — there is no array to mutate, so the caller's
 *       existing value is left untouched rather than fabricating an empty array.
 *   <li>{@code value == null} is treated as a no-op and the collection is returned unchanged. This
 *       mirrors {@link MergeFunction}'s lenient null-source handling and guards against
 *       accidentally stripping null elements when an optional request parameter is absent. To
 *       remove the request value only when present, guard the rule with a {@code condition} (e.g.
 *       {@code exists}).
 *   <li>When {@code field} is set but the element is not a Map or lacks the key, the extracted
 *       value is {@code null} and the element is kept (no accidental removal).
 * </ul>
 */
public class RemoveFunction implements ValueFunction {

  @Override
  public Object apply(Object input, Map<String, Object> args) {
    if (args == null || !args.containsKey("value")) {
      throw new IllegalArgumentException("remove: 'value' argument is required");
    }

    Object value = args.get("value");
    String field = getStringArg(args, "field");

    if (input == null) {
      return null;
    }

    Collection<?> collection = toCollection(input);

    // null value → nothing to remove (e.g. optional request parameter absent); return unchanged.
    if (value == null) {
      return new ArrayList<>(collection);
    }

    boolean hasField = field != null && !field.isEmpty();

    List<Object> result = new ArrayList<>();
    for (Object element : collection) {
      Object comparisonTarget = hasField ? extractField(element, field) : element;
      if (!Objects.equals(comparisonTarget, value)) {
        result.add(element);
      }
    }
    return result;
  }

  @Override
  public String name() {
    return "remove";
  }

  /**
   * Extract a field value from a Map element. Returns null if element is not a Map or field is
   * missing.
   */
  @SuppressWarnings("unchecked")
  private Object extractField(Object element, String field) {
    if (element instanceof Map) {
      return ((Map<String, Object>) element).get(field);
    }
    return null;
  }

  /** Helper method to extract string argument. */
  private static String getStringArg(Map<String, Object> args, String key) {
    Object value = args.get(key);
    return value != null ? value.toString() : null;
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
