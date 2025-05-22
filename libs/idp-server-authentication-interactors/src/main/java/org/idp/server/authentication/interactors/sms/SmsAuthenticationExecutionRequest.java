/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.authentication.interactors.sms;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class SmsAuthenticationExecutionRequest {

  Map<String, Object> values;

  public static SmsAuthenticationExecutionRequest empty() {
    return new SmsAuthenticationExecutionRequest(Map.of());
  }

  public SmsAuthenticationExecutionRequest(Map<String, Object> values) {
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

  public SmsAuthenticationExecutionRequest add(String key, Object value) {
    HashMap<String, Object> map = new HashMap<>(values);
    map.put(key, value);
    return new SmsAuthenticationExecutionRequest(map);
  }

  public String getValueAsString(String key) {
    return (String) values.get(key);
  }

  public boolean getValueAsBoolean(String key) {
    return (boolean) values.get(key);
  }

  public boolean containsKey(String key) {
    return values.containsKey(key);
  }

  public void forEach(BiConsumer<String, Object> action) {
    values.forEach(action);
  }

  public boolean exists() {
    return values != null && !values.isEmpty();
  }

  public Object getValue(String key) {
    return values.get(key);
  }
}
