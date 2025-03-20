package org.idp.server.core.sharedsignal;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.oauth.TokenIssuer;

public class EventTenant {

  TenantIdentifier id;
  TokenIssuer issuer;
  String name;

  public EventTenant() {}

  public EventTenant(TenantIdentifier id, TokenIssuer tokenIssuer, String name) {
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

  public TenantIdentifier id() {
    return id;
  }

  public String idAsString() {
    return id.value();
  }

  public TokenIssuer issuer() {
    return issuer;
  }

  public String issuerAsString() {
    return issuer.value();
  }

  public String name() {
    return name;
  }

  public boolean exists() {
    return Objects.nonNull(id) && !id.exists();
  }
}
