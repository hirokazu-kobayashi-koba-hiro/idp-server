package org.idp.server.core.tenant;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.basic.json.JsonReadable;
import org.idp.server.core.configuration.ServerIdentifier;
import org.idp.server.core.type.oauth.TokenIssuer;

public class Tenant implements JsonReadable {
  TenantIdentifier identifier;
  TenantName name;
  TenantType type;
  TenantServerAttribute serverAttribute;

  public Tenant() {}

  public Tenant(
      TenantIdentifier identifier,
      TenantName name,
      TenantType type,
      TenantServerAttribute serverAttribute) {
    this.identifier = identifier;
    this.name = name;
    this.type = type;
    this.serverAttribute = serverAttribute;
  }

  public TenantIdentifier identifier() {
    return identifier;
  }

  public String identifierValue() {
    return identifier.value();
  }

  public TenantName name() {
    return name;
  }

  public TenantType type() {
    return type;
  }

  public TenantServerAttribute serverAttribute() {
    return serverAttribute;
  }

  public ServerIdentifier serverIdentifier() {
    return serverAttribute.serverIdentifier();
  }

  public String issuer() {
    return serverAttribute.tokenIssuerValue();
  }

  public TokenIssuer tokenIssuer() {
    return serverAttribute.tokenIssuer();
  }

  public boolean isAdmin() {
    return type == TenantType.ADMIN;
  }

  public boolean isPublic() {
    return type == TenantType.PUBLIC;
  }

  public boolean exists() {
    return Objects.nonNull(identifier) && identifier.exists();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("id", identifier.value());
    map.put("name", name.value());
    map.put("type", type.name());
    map.put("attributes", serverAttribute.toMap());
    return map;
  }
}
