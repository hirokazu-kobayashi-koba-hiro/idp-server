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


package org.idp.server.basic.type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** ArrayValueMap */
public class ArrayValueMap {
  Map<String, String[]> values;

  public ArrayValueMap() {
    this.values = new HashMap<>();
  }

  public ArrayValueMap(Map<String, String[]> values) {
    this.values = values;
  }

  public String getFirstOrEmpty(String key) {
    if (!values.containsKey(key)) {
      return "";
    }
    return values.get(key)[0];
  }

  public boolean contains(String key) {
    return values.containsKey(key);
  }

  public boolean isEmpty() {
    return values.isEmpty();
  }

  public List<String> multiValueKeys() {
    List<String> keys = new ArrayList<>();
    values.forEach(
        (key, value) -> {
          if (value.length > 1) {
            keys.add(key);
          }
        });
    return keys;
  }

  public Map<String, String> singleValueMap() {
    Map<String, String> map = new HashMap<>();
    values.forEach(
        (key, value) -> {
          map.put(key, value[0]);
        });
    return map;
  }
}
