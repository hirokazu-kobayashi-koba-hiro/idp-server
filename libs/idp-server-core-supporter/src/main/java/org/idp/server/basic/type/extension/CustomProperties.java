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

package org.idp.server.basic.type.extension;

import java.util.HashMap;
import java.util.Map;

public class CustomProperties {
  Map<String, Object> values;

  public CustomProperties() {
    this.values = new HashMap<>();
  }

  public CustomProperties(Map<String, Object> values) {
    this.values = values;
  }

  public boolean exists() {
    return !values.isEmpty();
  }

  public Map<String, Object> values() {
    return values;
  }

  public String getValueAsStringOrEmpty(String key) {
    return (String) values.getOrDefault(key, "");
  }

  public Object getValue(String key) {
    return values.get(key);
  }

  public void setValue(String key, Object value) {
    values.put(key, value);
  }

  public boolean contains(String claimName) {
    return values.containsKey(claimName);
  }
}
