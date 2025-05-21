package org.idp.server.control_plane.management.tenant.io;

import java.util.Map;

public class TenantRequest {

  Map<String, Object> values;

  public TenantRequest(Map<String, Object> values) {
    this.values = values;
  }

  public Map<String, Object> toMap() {
    return values;
  }

  public Object get(String key) {
    return values.get(key);
  }
}
