package org.idp.server.control_plane.starter.io;

import java.util.Map;

public class IdpServerStarterRequest {

  Map<String, Object> values;

  public IdpServerStarterRequest(Map<String, Object> values) {
    this.values = values;
  }

  public Map<String, Object> toMap() {
    return values;
  }

  public Object get(String key) {
    return values.get(key);
  }
}
