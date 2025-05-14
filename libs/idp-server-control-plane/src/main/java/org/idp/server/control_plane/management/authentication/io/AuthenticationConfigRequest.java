package org.idp.server.control_plane.management.authentication.io;

import java.util.Map;

public class AuthenticationConfigRequest {

  Map<String, Object> values;

  public AuthenticationConfigRequest(Map<String, Object> values) {
    this.values = values;
  }

  public Map<String, Object> toMap() {
    return values;
  }

  public Object get(String key) {
    return values.get(key);
  }
}
