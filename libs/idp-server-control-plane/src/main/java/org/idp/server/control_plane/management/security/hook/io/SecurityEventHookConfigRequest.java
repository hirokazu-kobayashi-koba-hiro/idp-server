package org.idp.server.control_plane.management.security.hook.io;

import java.util.Map;

public class SecurityEventHookConfigRequest {

  Map<String, Object> values;

  public SecurityEventHookConfigRequest(Map<String, Object> values) {
    this.values = values;
  }

  public Map<String, Object> toMap() {
    return values;
  }

  public Object get(String key) {
    return values.get(key);
  }
}
