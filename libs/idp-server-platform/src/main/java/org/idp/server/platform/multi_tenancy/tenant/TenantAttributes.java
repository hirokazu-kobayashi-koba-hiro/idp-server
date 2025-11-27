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

package org.idp.server.platform.multi_tenancy.tenant;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import org.idp.server.platform.log.LoggerWrapper;

public class TenantAttributes {
  private static final LoggerWrapper log = LoggerWrapper.getLogger(TenantAttributes.class);

  Map<String, Object> values;

  public TenantAttributes() {
    this.values = new HashMap<>();
  }

  public TenantAttributes(Map<String, Object> values) {
    this.values = Objects.requireNonNullElseGet(values, HashMap::new);
  }

  public Map<String, Object> toMap() {
    return values;
  }

  public String optValueAsString(String key, String defaultValue) {
    if (containsKey(key)) {
      return (String) values.get(key);
    }
    return defaultValue;
  }

  public String getValueAsString(String key) {
    return (String) values.get(key);
  }

  public boolean containsKey(String key) {
    if (values == null || values.isEmpty()) {
      return false;
    }
    return values.containsKey(key);
  }

  public void forEach(BiConsumer<String, Object> action) {
    values.forEach(action);
  }

  public boolean exists() {
    return values != null && !values.isEmpty();
  }

  public TenantAttributes updateWith(TenantAttributes other) {
    values.putAll(other.values);
    return new TenantAttributes(values);
  }

  public boolean optValueAsBoolean(String key, boolean defaultValue) {
    if (values == null || values.isEmpty() || !containsKey(key)) {
      return defaultValue;
    }
    return (boolean) values.get(key);
  }

  public List<String> optValueAsStringList(String key, List<String> defaultValue) {
    if (values == null || values.isEmpty() || !containsKey(key)) {
      return defaultValue;
    }
    return (List<String>) values.get(key);
  }

  public ZoneId timezone() {
    String timezoneStr = optValueAsString("timezone", "UTC");
    try {
      return ZoneId.of(timezoneStr);
    } catch (Exception e) {
      log.warn(
          "Invalid timezone value '{}', falling back to UTC. Error: {}",
          timezoneStr,
          e.getMessage());
      return ZoneId.of("UTC");
    }
  }
}
