package org.idp.server.basic.type.extension;

import java.util.HashMap;
import java.util.Map;

public class CustomProperties {
  Map<String, Object> values;

  public CustomProperties() {
    this.values = new HashMap<>();
  }

  public CustomProperties(Map<String, Object> values) {
    this.values = values;
  }

  public boolean exists() {
    return !values.isEmpty();
  }

  public Map<String, Object> values() {
    return values;
  }

  public String getValueAsStringOrEmpty(String key) {
    return (String) values.getOrDefault(key, "");
  }
}
