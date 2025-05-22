/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.http;

import java.util.Map;
import java.util.function.BiConsumer;

public class HttpRequestHeaders {

  Map<String, String> values;

  public HttpRequestHeaders(Map<String, String> values) {
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
