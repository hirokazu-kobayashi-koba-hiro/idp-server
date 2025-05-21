package org.idp.server.core.multi_tenancy.tenant;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.json.JsonReadable;
import org.idp.server.basic.type.oauth.TokenIssuer;
import org.idp.server.platform.datasource.DatabaseType;
import org.idp.server.platform.dependency.protocol.AuthorizationProvider;

public class Tenant implements JsonReadable {
  TenantIdentifier identifier;
  TenantName name;
  TenantType type;
  TenantDomain domain;
  AuthorizationProvider authorizationProvider;
  DatabaseType databaseType;
  TenantAttributes attributes;
  TenantFeatures features;

  public Tenant() {}

  public Tenant(
      TenantIdentifier identifier,
      TenantName name,
      TenantType type,
      TenantDomain domain,
      AuthorizationProvider authorizationProvider,
      DatabaseType databaseType) {
    this(
        identifier,
        name,
        type,
        domain,
        authorizationProvider,
        databaseType,
        new TenantAttributes(Map.of()));
  }

  public Tenant(
      TenantIdentifier identifier,
      TenantName name,
      TenantType type,
      TenantDomain domain,
      AuthorizationProvider authorizationProvider,
      DatabaseType databaseType,
      TenantAttributes attributes) {
    this.identifier = identifier;
    this.name = name;
    this.type = type;
    this.domain = domain;
    this.authorizationProvider = authorizationProvider;
    this.databaseType = databaseType;
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
    map.put("authorization_provider", authorizationProvider.name());
    map.put("database_type", databaseType.name());
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

  public AuthorizationProvider authorizationProvider() {
    return authorizationProvider;
  }

  public DatabaseType databaseType() {
    return databaseType;
  }

  public Map<String, Object> attributesAsMap() {
    return attributes.toMap();
  }

  public TenantFeatures features() {
    return features;
  }

  public Tenant updateDomain(TenantDomain domain) {

    return new Tenant(
        identifier, name, type, domain, authorizationProvider, databaseType, attributes);
  }

  public Tenant updateWithAttributes(TenantAttributes attributes) {
    return new Tenant(
        identifier, name, type, domain, authorizationProvider, databaseType, attributes);
  }
}
