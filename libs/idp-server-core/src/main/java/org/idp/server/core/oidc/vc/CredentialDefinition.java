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


package org.idp.server.core.oidc.vc;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CredentialDefinition {

  Map<String, Object> values;

  public CredentialDefinition(Map<String, Object> values) {
    this.values = values;
  }

  public List<String> type() {
    return getListOrEmpty("type");
  }

  public boolean hasType() {
    return values.containsKey("type");
  }

  public List<String> context() {
    return getListOrEmpty("@context");
  }

  public boolean hasContext() {
    return values.containsKey("@context");
  }

  public List<String> getListOrEmpty(String key) {
    Object value = values.get(key);
    if (Objects.isNull(value)) {
      return List.of();
    }
    return (List<String>) value;
  }

  public String getValueOrEmpty(String key) {
    Object value = values.get(key);
    if (Objects.isNull(value)) {
      return "";
    }
    return (String) value;
  }

  public Map<String, Object> getMapOrEmpty(String key) {
    Object value = values.get(key);
    if (Objects.isNull(value)) {
      return Map.of();
    }
    return (Map<String, Object>) value;
  }
}
