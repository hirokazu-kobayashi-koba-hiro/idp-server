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

package org.idp.server.core.oidc.type.oauth;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class CustomParams {
  Map<String, String> values;

  public CustomParams() {
    values = new HashMap<>();
  }

  public CustomParams(Map<String, String> values) {
    this.values = values;
  }

  public Map<String, String> values() {
    return values;
  }

  public boolean exists() {
    return Objects.nonNull(values) && !values.isEmpty();
  }

  public String getValueAsStringOrEmpty(String key) {
    return values.getOrDefault(key, "");
  }
}
