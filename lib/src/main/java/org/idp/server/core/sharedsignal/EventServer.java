package org.idp.server.core.sharedsignal;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.type.oauth.TokenIssuer;

public class EventServer {

  TokenIssuer id;
  String name;

  public EventServer() {}

  public EventServer(TokenIssuer id, String name) {
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

  public TokenIssuer issuer() {
    return id;
  }
}
