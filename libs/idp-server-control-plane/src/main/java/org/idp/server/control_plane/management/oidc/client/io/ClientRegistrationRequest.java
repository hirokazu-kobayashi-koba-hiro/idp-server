package org.idp.server.control_plane.management.oidc.client.io;

import java.util.Map;

public class ClientRegistrationRequest {

  Map<String, Object> values;

  public ClientRegistrationRequest(Map<String, Object> values) {
    this.values = values;
  }

  public Map<String, Object> toMap() {
    return values;
  }

  public Object get(String key) {
    return values.get(key);
  }

}
