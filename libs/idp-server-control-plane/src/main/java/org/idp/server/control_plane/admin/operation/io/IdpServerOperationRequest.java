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

package org.idp.server.control_plane.admin.operation.io;

import java.util.Map;

public class IdpServerOperationRequest {

  Map<String, Object> values;

  /** A request without a body is read as an empty one, so every option takes its default. */
  public IdpServerOperationRequest(Map<String, Object> values) {
    this.values = values == null ? Map.of() : values;
  }

  public Map<String, Object> toMap() {
    return values;
  }

  public Object get(String key) {
    return values.get(key);
  }

  public int optValueAsInt(String key, int defaultValue) {
    if (!values.containsKey(key)) {
      return defaultValue;
    }
    return Integer.parseInt(values.get(key).toString());
  }

  /**
   * The value as an integer within {@code [min, max]}, the default when absent, or {@code null}
   * when present but not such an integer.
   */
  public Integer optBoundedInt(String key, int defaultValue, int min, int max) {
    Object value = values.get(key);
    if (value == null) {
      return defaultValue;
    }
    long number;
    if (value instanceof Integer || value instanceof Long) {
      number = ((Number) value).longValue();
    } else if (value instanceof String text && text.matches("-?[0-9]{1,18}")) {
      // Accepted as before: callers have sent the number as a string.
      number = Long.parseLong(text);
    } else {
      return null;
    }
    if (number < min || number > max) {
      return null;
    }
    return (int) number;
  }

  /** The value as a non-blank string, or {@code null}. */
  public String optString(String key) {
    Object value = values.get(key);
    if (!(value instanceof String text) || text.isBlank()) {
      return null;
    }
    return text;
  }
}
