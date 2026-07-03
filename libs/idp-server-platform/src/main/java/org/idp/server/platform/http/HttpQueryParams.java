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

package org.idp.server.platform.http;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class HttpQueryParams {
  Map<String, String> values = new HashMap<>();

  public HttpQueryParams() {}

  public HttpQueryParams(Map<String, String> values) {
    values.forEach(this::add);
  }

  public static HttpQueryParams fromMapObject(Map<String, Object> values) {
    HttpQueryParams params = new HttpQueryParams();
    // Objects.toString(v, null) passes a null value through to add(), which skips it, rather than
    // calling v.toString() and throwing NPE. A null value in a form-encoded body must not break
    // assembly, mirroring the add() null guard. (#1630)
    values.forEach((k, v) -> params.add(k, Objects.toString(v, null)));
    return params;
  }

  /**
   * Adds a query parameter, skipping null or empty values instead of encoding them. {@link
   * UrlParameterSanitizer#encodeQueryValue} would throw NPE on null ({@code URLEncoder.encode(null,
   * ...)}), and {@link #params()} already omits empty values — so an omitted optional field (e.g.
   * {@code scope} in {@code client_credentials}) must not break request assembly. (#1630)
   */
  public void add(String key, String value) {
    if (value == null || value.isEmpty()) {
      return;
    }
    String urlEncodedKey = UrlParameterSanitizer.encodeQueryKey(key);
    String urlEncodedValue = UrlParameterSanitizer.encodeQueryValue(value);
    values.put(urlEncodedKey, urlEncodedValue);
  }

  public String params() {
    StringBuilder stringBuilder = new StringBuilder();
    Set<Map.Entry<String, String>> entries = values.entrySet();
    entries.forEach(
        entry -> {
          if (Objects.nonNull(entry.getValue()) && !entry.getValue().isEmpty()) {
            if (!stringBuilder.toString().isEmpty()) {
              stringBuilder.append("&");
            }
            stringBuilder.append(entry.getKey());
            stringBuilder.append("=");
            stringBuilder.append(entry.getValue());
          }
        });
    return stringBuilder.toString();
  }
}
