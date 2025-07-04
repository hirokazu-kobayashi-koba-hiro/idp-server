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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class HttpQueryParams {
  Map<String, String> values = new HashMap<>();

  public HttpQueryParams() {}

  public HttpQueryParams(Map<String, String> values) {
    this.values.putAll(values);
  }

  public void add(String key, String value) {
    String urlEncodedValue = URLEncoder.encode(value, StandardCharsets.UTF_8);
    values.put(key, urlEncodedValue);
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
