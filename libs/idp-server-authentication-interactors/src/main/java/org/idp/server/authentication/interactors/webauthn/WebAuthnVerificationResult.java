/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.authentication.interactors.webauthn;

import java.util.Map;
import java.util.function.BiConsumer;

public class WebAuthnVerificationResult {

  Map<String, Object> values;

  public WebAuthnVerificationResult(Map<String, Object> values) {
    this.values = values;
  }

  public Map<String, Object> toMap() {
    return values;
  }

  public String getValueAsString(String key) {
    return (String) values.get(key);
  }

  public String optValueAsString(String key, String defaultValue) {
    if (containsKey(key)) {
      return (String) values.get(key);
    }
    return defaultValue;
  }

  public int getValueAsInt(String key) {
    return (int) values.get(key);
  }

  public int optValueAsInt(String key, int defaultValue) {
    if (containsKey(key)) {
      return (int) values.get(key);
    }
    return defaultValue;
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

  public String getUserId() {
    return (String) values.get("user_id");
  }
}
