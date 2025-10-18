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
import org.idp.server.platform.dependency.protocol.AuthorizationProvider;
import org.idp.server.platform.multi_tenancy.tenant.config.CorsConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.config.SessionConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.config.UIConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.policy.TenantIdentityPolicy;
import org.idp.server.platform.security.event.SecurityEventUserAttributeConfiguration;
import org.idp.server.platform.security.log.SecurityEventLogConfiguration;

public class Tenant {
  TenantIdentifier identifier;
  TenantName name;
  TenantType type;
  TenantDomain domain;
  AuthorizationProvider authorizationProvider;
  TenantAttributes attributes;
  TenantFeatures features;
  UIConfiguration uiConfiguration;
  CorsConfiguration corsConfiguration;
  SessionConfiguration sessionConfiguration;
  SecurityEventLogConfiguration securityEventLogConfiguration;
  SecurityEventUserAttributeConfiguration securityEventUserAttributeConfiguration;
  TenantIdentityPolicy identityPolicyConfig;

  public Tenant() {}

  public Tenant(
      TenantIdentifier identifier,
      TenantName name,
      TenantType type,
      TenantDomain domain,
      AuthorizationProvider authorizationProvider,
      TenantAttributes attributes,
      UIConfiguration uiConfiguration,
      CorsConfiguration corsConfiguration,
      SessionConfiguration sessionConfiguration,
      SecurityEventLogConfiguration securityEventLogConfiguration,
      SecurityEventUserAttributeConfiguration securityEventUserAttributeConfiguration,
      TenantIdentityPolicy identityPolicyConfig) {
    this.identifier = identifier;
    this.name = name;
    this.type = type;
    this.domain = domain;
    this.authorizationProvider = authorizationProvider;
    this.attributes = attributes;
    this.uiConfiguration = uiConfiguration;
    this.corsConfiguration = corsConfiguration;
    this.sessionConfiguration = sessionConfiguration;
    this.securityEventLogConfiguration = securityEventLogConfiguration;
    this.securityEventUserAttributeConfiguration = securityEventUserAttributeConfiguration;
    this.identityPolicyConfig = identityPolicyConfig;
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
    map.put("domain", domain.value());
    map.put("authorization_provider", authorizationProvider.name());
    map.put("attributes", attributes.toMap());
    map.put("ui_config", uiConfiguration.toMap());
    map.put("cors_config", corsConfiguration.toMap());
    map.put("session_config", sessionConfiguration.toMap());
    map.put("security_event_log_config", securityEventLogConfiguration.toMap());
    map.put("security_event_user_config", securityEventUserAttributeConfiguration.toMap());
    map.put("identity_policy_config", identityPolicyConfig.toMap());
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

  public Map<String, Object> attributesAsMap() {
    return attributes.toMap();
  }

  public TenantFeatures features() {
    return features;
  }

  public UIConfiguration uiConfiguration() {
    return uiConfiguration;
  }

  public CorsConfiguration corsConfiguration() {
    return corsConfiguration;
  }

  public SessionConfiguration sessionConfiguration() {
    return sessionConfiguration;
  }

  /**
   * Returns session cookie name with tenant ID suffix for default configuration.
   *
   * <p>For multi-tenant isolation, generates tenant-specific cookie name when no custom name is
   * configured. Custom cookie names are returned as-is.
   *
   * @return cookie name with tenant suffix (e.g., "IDP_SERVER_SESSION_a1b2c3d4") when not
   *     configured, or the configured name
   */
  public String sessionCookieName() {
    // Generate tenant-specific default name when not configured
    if (!sessionConfiguration.hasCookieName()) {
      String tenantIdPrefix = identifier.value().substring(0, 8);
      return "IDP_SERVER_SESSION_" + tenantIdPrefix;
    }
    return sessionConfiguration.cookieName();
  }

  public SecurityEventLogConfiguration securityEventLogConfiguration() {
    return securityEventLogConfiguration;
  }

  public SecurityEventUserAttributeConfiguration securityEventUserAttributeConfiguration() {
    return securityEventUserAttributeConfiguration;
  }

  public TenantIdentityPolicy identityPolicyConfig() {
    return identityPolicyConfig;
  }
}
