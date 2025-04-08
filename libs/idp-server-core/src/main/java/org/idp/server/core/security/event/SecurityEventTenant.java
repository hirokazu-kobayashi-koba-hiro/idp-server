package org.idp.server.core.security.event;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.type.oauth.TokenIssuer;

public class SecurityEventTenant {

  String id;
  String issuer;
  String name;

  public SecurityEventTenant() {}

  public SecurityEventTenant(String id, String tokenIssuer, String name) {
    this.id = id;
    this.issuer = tokenIssuer;
    this.name = name;
  }

  public Map<String, Object> toMap() {
    HashMap<String, Object> result = new HashMap<>();
    if (id != null) {
      result.put("id", id);
    }
    if (issuer != null) {
      result.put("iss", issuer);
    }
    if (name != null) {
      result.put("name", name);
    }
    return result;
  }

  public String id() {
    return id;
  }

  public TokenIssuer issuer() {
    return new TokenIssuer(issuer);
  }

  public String issuerAsString() {
    return issuer;
  }

  public String name() {
    return name;
  }

  public boolean exists() {
    return Objects.nonNull(id) && !id.isEmpty();
  }
}
