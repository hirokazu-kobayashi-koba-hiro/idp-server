package org.idp.server.control_plane.management.oidc.authorization.io;

import java.util.Map;

public class AuthorizationServerUpdateRequest {

  Map<String, Object> values;

  public AuthorizationServerUpdateRequest(Map<String, Object> values) {
    this.values = values;
  }

  public Map<String, Object> toMap() {
    return values;
  }

  public Object get(String key) {
    return values.get(key);
  }
}
