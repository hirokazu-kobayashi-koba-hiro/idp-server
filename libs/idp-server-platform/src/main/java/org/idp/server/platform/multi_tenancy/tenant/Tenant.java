/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.platform.multi_tenancy.tenant;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.idp.server.platform.datasource.DatabaseType;
import org.idp.server.platform.dependency.protocol.AuthorizationProvider;
import org.idp.server.platform.security.event.SecurityEventUserAttributeConfiguration;

public class Tenant {
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

  public UUID identifierUUID() {
    return identifier.valueAsUuid();
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

  public String tokenIssuer() {
    return domain.toTokenIssuer();
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

  public Tenant updateWithDomain(TenantDomain domain) {

    return new Tenant(
        identifier, name, type, domain, authorizationProvider, databaseType, attributes);
  }

  public Tenant updateWithAttributes(TenantAttributes attributes) {
    return new Tenant(
        identifier, name, type, domain, authorizationProvider, databaseType, attributes);
  }

  public SecurityEventUserAttributeConfiguration getSecurityEventUserAttributeConfiguration() {
    return SecurityEventUserAttributeConfiguration.fromTenantAttributes(this.attributes);
  }
}
