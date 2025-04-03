package org.idp.server.core.security.event;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.type.oauth.RequestedClientId;

public class SecurityEventClient {

  RequestedClientId id;
  String name;

  public SecurityEventClient() {}

  public SecurityEventClient(RequestedClientId id, String name) {
    this.id = id;
    this.name = name;
  }

  public Map<String, Object> toMap() {
    HashMap<String, Object> result = new HashMap<>();
    if (id != null) {
      result.put("id", id);
    }
    if (name != null) {
      result.put("name", name);
    }
    return result;
  }

  public String id() {
    return id.value();
  }

  public String name() {
    return name;
  }

  public boolean exists() {
    return Objects.nonNull(id) && !id.exists();
  }

  public RequestedClientId clientId() {
    return id;
  }
}
