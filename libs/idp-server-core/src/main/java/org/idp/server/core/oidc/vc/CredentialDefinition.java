/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
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
