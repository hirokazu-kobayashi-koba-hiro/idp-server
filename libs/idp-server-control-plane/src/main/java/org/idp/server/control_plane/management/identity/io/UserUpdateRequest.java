package org.idp.server.control_plane.management.identity.io;

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

  public boolean isDryRun() {
    if (!values.containsKey("dry_run")) {
      return false;
    }
    return (boolean) values.get("dry_run");
  }
}
