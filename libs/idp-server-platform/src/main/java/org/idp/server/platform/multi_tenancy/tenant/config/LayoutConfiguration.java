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

package org.idp.server.platform.multi_tenancy.tenant.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Layout configuration for Universal Login
 *
 * <p>Encapsulates tenant-specific layout settings including template selection and display options.
 */
public class LayoutConfiguration {

  private final String template;
  private final boolean showPoweredBy;

  public LayoutConfiguration() {
    this.template = "default";
    this.showPoweredBy = true;
  }

  public LayoutConfiguration(Map<String, Object> values) {
    Map<String, Object> safeValues = Objects.requireNonNullElseGet(values, HashMap::new);
    this.template = extractString(safeValues, "template", "default");
    this.showPoweredBy = extractBoolean(safeValues, "show_powered_by", true);
  }

  /**
   * Returns the template type
   *
   * @return template type ("default", "minimal", or "split")
   */
  public String template() {
    return template;
  }

  /**
   * Returns whether to show "Powered by IDP" footer
   *
   * @return true if should show powered by footer
   */
  public boolean showPoweredBy() {
    return showPoweredBy;
  }

  /**
   * Returns the configuration as a map
   *
   * @return configuration map
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("template", template);
    map.put("show_powered_by", showPoweredBy);
    return map;
  }

  private static String extractString(Map<String, Object> values, String key, String defaultValue) {
    if (values == null || values.isEmpty() || !values.containsKey(key)) {
      return defaultValue;
    }
    Object value = values.get(key);
    return value != null ? value.toString() : defaultValue;
  }

  private static boolean extractBoolean(
      Map<String, Object> values, String key, boolean defaultValue) {
    if (values == null || values.isEmpty() || !values.containsKey(key)) {
      return defaultValue;
    }
    Object value = values.get(key);
    if (value instanceof Boolean) {
      return (Boolean) value;
    }
    return defaultValue;
  }
}
