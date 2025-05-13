package org.idp.server.control_plane.management.authentication.io;

import java.util.Map;

public class AuthenticationConfigRegistrationRequest {

  Map<String, Object> values;

  public AuthenticationConfigRegistrationRequest(Map<String, Object> values) {
    this.values = values;
  }

  public Map<String, Object> toMap() {
    return values;
  }

  public Object get(String key) {
    return values.get(key);
  }

}
