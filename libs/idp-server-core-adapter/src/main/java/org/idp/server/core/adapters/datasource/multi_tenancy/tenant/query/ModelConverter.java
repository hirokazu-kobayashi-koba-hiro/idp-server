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

package org.idp.server.core.adapters.datasource.multi_tenancy.tenant.query;

import java.util.Map;
import org.idp.server.platform.datasource.DatabaseType;
import org.idp.server.platform.dependency.protocol.AuthorizationProvider;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.tenant.*;
import org.idp.server.platform.multi_tenancy.tenant.config.CorsConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.config.SessionConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.config.UIConfiguration;
import org.idp.server.platform.security.event.SecurityEventUserAttributeConfiguration;
import org.idp.server.platform.security.log.SecurityEventLogConfiguration;

class ModelConverter {

  private static final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  static Tenant convert(Map<String, String> result) {

    TenantIdentifier tenantIdentifier = new TenantIdentifier(result.getOrDefault("id", ""));
    TenantName tenantName = new TenantName(result.getOrDefault("name", ""));
    TenantType tenantType = TenantType.valueOf(result.getOrDefault("type", ""));
    TenantDomain tenantDomain = new TenantDomain(result.getOrDefault("domain", ""));
    AuthorizationProvider authorizationProvider =
        new AuthorizationProvider(result.getOrDefault("authorization_provider", ""));
    DatabaseType databaseType = DatabaseType.of(result.getOrDefault("database_type", ""));
    TenantAttributes tenantAttributes = convertAttributes(result.getOrDefault("attributes", ""));
    UIConfiguration uiConfiguration = convertUIConfiguration(result.getOrDefault("ui_config", ""));
    CorsConfiguration corsConfiguration =
        convertCorsConfiguration(result.getOrDefault("cors_config", ""));
    SessionConfiguration sessionConfiguration =
        convertSessionConfiguration(result.getOrDefault("session_config", ""));
    SecurityEventLogConfiguration securityEventLogConfiguration =
        convertSecurityEventLogConfiguration(result.getOrDefault("security_event_log_config", ""));
    SecurityEventUserAttributeConfiguration securityEventUserAttributeConfiguration =
        convertSecurityEventUserAttributeConfiguration(
            result.getOrDefault("security_event_user_config", ""));
    TenantAttributes identityPolicyConfig =
        convertAttributes(result.getOrDefault("identity_policy_config", ""));

    return new Tenant(
        tenantIdentifier,
        tenantName,
        tenantType,
        tenantDomain,
        authorizationProvider,
        databaseType,
        tenantAttributes,
        uiConfiguration,
        corsConfiguration,
        sessionConfiguration,
        securityEventLogConfiguration,
        securityEventUserAttributeConfiguration,
        identityPolicyConfig);
  }

  private static TenantAttributes convertAttributes(String value) {
    if (value == null || value.isEmpty()) {
      return new TenantAttributes();
    }
    try {

      JsonNodeWrapper jsonNodeWrapper = jsonConverter.readTree(value);
      Map<String, Object> attributesMap = jsonNodeWrapper.toMap();
      return new TenantAttributes(attributesMap);
    } catch (Exception exception) {
      return new TenantAttributes();
    }
  }

  private static UIConfiguration convertUIConfiguration(String value) {
    if (value == null || value.isEmpty()) {
      return new UIConfiguration();
    }
    try {
      JsonNodeWrapper jsonNodeWrapper = jsonConverter.readTree(value);
      Map<String, Object> configMap = jsonNodeWrapper.toMap();
      return new UIConfiguration(configMap);
    } catch (Exception exception) {
      return new UIConfiguration();
    }
  }

  private static CorsConfiguration convertCorsConfiguration(String value) {
    if (value == null || value.isEmpty()) {
      return new CorsConfiguration();
    }
    try {
      JsonNodeWrapper jsonNodeWrapper = jsonConverter.readTree(value);
      Map<String, Object> configMap = jsonNodeWrapper.toMap();
      return new CorsConfiguration(configMap);
    } catch (Exception exception) {
      return new CorsConfiguration();
    }
  }

  private static SessionConfiguration convertSessionConfiguration(String value) {
    if (value == null || value.isEmpty()) {
      return new SessionConfiguration();
    }
    try {
      JsonNodeWrapper jsonNodeWrapper = jsonConverter.readTree(value);
      Map<String, Object> configMap = jsonNodeWrapper.toMap();
      return new SessionConfiguration(configMap);
    } catch (Exception exception) {
      return new SessionConfiguration();
    }
  }

  private static SecurityEventLogConfiguration convertSecurityEventLogConfiguration(String value) {
    if (value == null || value.isEmpty()) {
      return new SecurityEventLogConfiguration();
    }
    try {
      JsonNodeWrapper jsonNodeWrapper = jsonConverter.readTree(value);
      Map<String, Object> configMap = jsonNodeWrapper.toMap();
      return new SecurityEventLogConfiguration(configMap);
    } catch (Exception exception) {
      return new SecurityEventLogConfiguration();
    }
  }

  private static SecurityEventUserAttributeConfiguration
      convertSecurityEventUserAttributeConfiguration(String value) {
    if (value == null || value.isEmpty()) {
      return new SecurityEventUserAttributeConfiguration();
    }
    try {
      JsonNodeWrapper jsonNodeWrapper = jsonConverter.readTree(value);
      Map<String, Object> configMap = jsonNodeWrapper.toMap();
      return new SecurityEventUserAttributeConfiguration(configMap);
    } catch (Exception exception) {
      return new SecurityEventUserAttributeConfiguration();
    }
  }
}
