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

package org.idp.server.core.oidc.authentication;

import java.util.HashMap;
import java.util.Map;

public class AuthenticationTransactionAttributes {

  Map<String, Object> values;

  public AuthenticationTransactionAttributes() {
    this.values = new HashMap<>();
  }

  public AuthenticationTransactionAttributes(Map<String, Object> values) {
    this.values = values;
  }

  public Map<String, Object> toMap() {
    return values;
  }

  public String getValueOrEmpty(String key) {
    return (String) values.getOrDefault(key, "");
  }

  public boolean getValueAsBoolean(String key) {
    if (!values.containsKey(key)) {
      return false;
    }
    return (Boolean) values.getOrDefault(key, false);
  }

  public Integer getValueAsInteger(String key) {
    if (!values.containsKey(key)) {
      return null;
    }
    return (Integer) values.get(key);
  }

  public boolean containsKey(String key) {
    return values.containsKey(key);
  }

  public boolean exists() {
    return values != null && !values.isEmpty();
  }
}
