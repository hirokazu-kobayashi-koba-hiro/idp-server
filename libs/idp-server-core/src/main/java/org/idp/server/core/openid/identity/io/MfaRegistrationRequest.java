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

package org.idp.server.core.openid.identity.io;

import java.util.HashMap;
import java.util.Map;

public class MfaRegistrationRequest {

  Map<String, Object> values;

  public MfaRegistrationRequest() {
    this.values = new HashMap<>();
  }

  public MfaRegistrationRequest(Map<String, Object> values) {
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

  public boolean exists() {
    return values != null && !values.isEmpty();
  }

  public String getAction() {
    return getValueOrEmpty("action");
  }

  public boolean isResetAction() {
    return "reset".equals(getAction());
  }
}
