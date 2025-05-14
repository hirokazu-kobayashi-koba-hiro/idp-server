package org.idp.server.control_plane.management.federation.io;

import java.util.Map;

public class FederationConfigRequest {

  Map<String, Object> values;

  public FederationConfigRequest(Map<String, Object> values) {
    this.values = values;
  }

  public Map<String, Object> toMap() {
    return values;
  }

  public Object get(String key) {
    return values.get(key);
  }
}
