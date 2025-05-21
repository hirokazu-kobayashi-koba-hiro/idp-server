package org.idp.server.platform.multi_tenancy.tenant;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class TenantAttributes {
  Map<String, Object> values;

  public TenantAttributes() {
    this.values = new HashMap<>();
  }

  public TenantAttributes(Map<String, Object> values) {
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

  public String getValueAsString(String key) {
    return (String) values.get(key);
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

  public TenantAttributes updateWith(TenantAttributes other) {
    values.putAll(other.values);
    return new TenantAttributes(values);
  }
}
