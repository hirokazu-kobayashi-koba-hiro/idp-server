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

public class HttpRequestBaseParams {

  Map<String, Object> values;

  public HttpRequestBaseParams() {}

  public HttpRequestBaseParams(Map<String, Object> values) {
    this.values = values;
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

  public Map<String, Object> optValueAsMap(String key) {
    if (containsKey(key)) {
      return (Map<String, Object>) values.get(key);
    }
    return Map.of();
  }

  public Object getValue(String key) {
    return values.get(key);
  }

  public boolean containsKey(String key) {
    return values.containsKey(key);
  }
}
