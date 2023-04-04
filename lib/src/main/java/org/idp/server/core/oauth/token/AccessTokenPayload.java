package org.idp.server.core.oauth.token;

import java.util.HashMap;
import java.util.Map;

public class AccessTokenPayload {
  Map<String, Object> values;

  public AccessTokenPayload() {
    this.values = new HashMap<>();
  }

  public AccessTokenPayload(Map<String, Object> values) {
    this.values = values;
  }

  public void add(String key, Object value) {
    values.put(key, value);
  }

  public Map<String, Object> values() {
    return values;
  }
}
