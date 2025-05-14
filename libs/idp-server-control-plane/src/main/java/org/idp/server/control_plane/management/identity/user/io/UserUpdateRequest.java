package org.idp.server.control_plane.management.identity.user.io;

import java.util.Map;

public class UserUpdateRequest {

  Map<String, Object> values;

  public UserUpdateRequest(Map<String, Object> values) {
    this.values = values;
  }

  public Map<String, Object> toMap() {
    return values;
  }

  public Object get(String key) {
    return values.get(key);
  }
}
