package org.idp.server.control_plane.admin.tenant.io;

import java.util.Map;

public class TenantInitializationRequest {

  Map<String, Object> values;

  public TenantInitializationRequest(Map<String, Object> values) {
    this.values = values;
  }

  public Map<String, Object> toMap() {
    return values;
  }

  public Object get(String key) {
    return values.get(key);
  }
}
