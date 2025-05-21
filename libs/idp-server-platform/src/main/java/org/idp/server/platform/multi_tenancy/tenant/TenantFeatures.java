package org.idp.server.platform.multi_tenancy.tenant;

import java.util.HashMap;
import java.util.Map;

public class TenantFeatures {
  Map<String, TenantFeature> values;

  public TenantFeatures() {
    this.values = new HashMap<>();
  }

  public TenantFeatures(Map<String, TenantFeature> values) {
    this.values = values;
  }

  public TenantFeature get(String name) {
    return values.get(name);
  }

  public boolean containsKey(String name) {
    return values.containsKey(name);
  }

  public TenantFeatures updateWith(TenantFeatures other) {
    values.putAll(other.values);
    return new TenantFeatures(values);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    values.forEach((k, v) -> map.put(k, v.toMap()));
    return map;
  }
}
