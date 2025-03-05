package org.idp.server.core.sharedsignal;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class EventClient {

  String id;
  String name;

  public EventClient() {}

  public EventClient(String id, String name) {
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
    return id;
  }

  public String name() {
    return name;
  }

  public boolean exists() {
    return Objects.nonNull(id) && !id.isEmpty();
  }
}
