package org.idp.server.core.tenant;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.basic.datasource.DatabaseType;
import org.idp.server.core.basic.dependency.protcol.AuthorizationProtocolProvider;
import org.idp.server.core.basic.json.JsonReadable;
import org.idp.server.core.type.oauth.TokenIssuer;

public class Tenant implements JsonReadable {
  TenantIdentifier identifier;
  TenantName name;
  TenantType type;
  TenantDomain domain;
  TenantAttributes attributes;

  public Tenant() {}

  public Tenant(
      TenantIdentifier identifier, TenantName name, TenantType type, TenantDomain domain) {
    this(identifier, name, type, domain, new TenantAttributes(Map.of()));
  }

  public Tenant(
      TenantIdentifier identifier,
      TenantName name,
      TenantType type,
      TenantDomain domain,
      TenantAttributes attributes) {
    this.identifier = identifier;
    this.name = name;
    this.type = type;
    this.domain = domain;
    this.attributes = attributes;
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
    map.put("attributes", attributes.toMap());
    return map;
  }

  public TokenIssuer tokenIssuer() {
    return domain.toTokenIssuer();
  }

  public String tokenIssuerValue() {
    return tokenIssuer().value();
  }

  public TenantDomain domain() {
    return domain;
  }

  public TenantAttributes attributes() {
    return attributes;
  }

  public AuthorizationProtocolProvider authorizationProtocolProvider() {
    return attributes.authorizationProtocolProvider();
  }

  public DatabaseType databaseType() {
    return attributes.databaseType();
  }

  public Map<String, Object> attributesAsMap() {
    return attributes.toMap();
  }
}
