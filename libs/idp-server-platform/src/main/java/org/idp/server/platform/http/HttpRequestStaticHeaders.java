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

import java.util.Map;
import java.util.function.BiConsumer;

public class HttpRequestStaticHeaders {

  Map<String, String> values;

  public HttpRequestStaticHeaders(Map<String, String> values) {
    this.values = values;
  }

  public Map<String, String> toMap() {
    return values;
  }

  public String optValueAsString(String key, String defaultValue) {
    if (containsKey(key)) {
      return values.get(key);
    }
    return defaultValue;
  }

  public boolean containsKey(String key) {
    return values.containsKey(key);
  }

  public void forEach(BiConsumer<String, String> action) {
    values.forEach(action);
  }

  public String getValueAsString(String key) {
    return values.get(key);
  }
}
