/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.type.oauth;

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
