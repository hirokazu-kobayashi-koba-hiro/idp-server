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
import org.idp.server.platform.dependency.protocol.AuthorizationProvider;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.*;
import org.idp.server.platform.multi_tenancy.tenant.config.CorsConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.config.SessionConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.config.UIConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.policy.TenantIdentityPolicy;
import org.idp.server.platform.security.event.SecurityEventUserAttributeConfiguration;
import org.idp.server.platform.security.log.SecurityEventLogConfiguration;

class ModelConverter {

  private static final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
  private static final LoggerWrapper log = LoggerWrapper.getLogger(ModelConverter.class);

  static Tenant convert(Map<String, String> result) {

    TenantIdentifier tenantIdentifier = new TenantIdentifier(result.getOrDefault("id", ""));
    TenantName tenantName = new TenantName(result.getOrDefault("name", ""));
    TenantType tenantType = TenantType.valueOf(result.getOrDefault("type", ""));
    TenantDomain tenantDomain = new TenantDomain(result.getOrDefault("domain", ""));
    AuthorizationProvider authorizationProvider =
        new AuthorizationProvider(result.getOrDefault("authorization_provider", ""));
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
    TenantIdentityPolicy identityPolicyConfig =
        convertIdentityPolicyConfig(result.getOrDefault("identity_policy_config", ""));
    OrganizationIdentifier mainOrganizationIdentifier =
        new OrganizationIdentifier(result.getOrDefault("main_organization_id", ""));
    boolean enabled = parseDatabaseBoolean(result.get("enabled"), true);

    return new Tenant(
        tenantIdentifier,
        tenantName,
        tenantType,
        tenantDomain,
        authorizationProvider,
        tenantAttributes,
        uiConfiguration,
        corsConfiguration,
        sessionConfiguration,
        securityEventLogConfiguration,
        securityEventUserAttributeConfiguration,
        identityPolicyConfig,
        mainOrganizationIdentifier,
        enabled);
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
      log.warn(
          "Failed to convert attributes, using empty attributes. Value: {}, Error: {}",
          value,
          exception.getMessage());
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
      log.warn(
          "Failed to convert ui_config, using default configuration. Value: {}, Error: {}",
          value,
          exception.getMessage());
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
      log.warn(
          "Failed to convert cors_config, using default configuration. Value: {}, Error: {}",
          value,
          exception.getMessage());
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
      log.warn(
          "Failed to convert session_config, using default configuration. Value: {}, Error: {}",
          value,
          exception.getMessage());
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
      log.warn(
          "Failed to convert security_event_log_config, using default configuration. Value: {}, Error: {}",
          value,
          exception.getMessage());
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
      log.warn(
          "Failed to convert security_event_user_config, using default configuration. Value: {}, Error: {}",
          value,
          exception.getMessage());
      return new SecurityEventUserAttributeConfiguration();
    }
  }

  private static TenantIdentityPolicy convertIdentityPolicyConfig(String value) {
    if (value == null || value.isEmpty()) {
      return TenantIdentityPolicy.defaultPolicy();
    }
    try {
      JsonNodeWrapper jsonNodeWrapper = jsonConverter.readTree(value);
      Map<String, Object> configMap = jsonNodeWrapper.toMap();
      return TenantIdentityPolicy.fromMap(configMap);
    } catch (Exception exception) {
      log.warn(
          "Failed to convert identity_policy_config, using default policy. Value: {}, Error: {}",
          value,
          exception.getMessage());
      return TenantIdentityPolicy.defaultPolicy();
    }
  }

  /**
   * Parses database boolean values to Java boolean.
   *
   * <p>Handles different database boolean representations:
   *
   * <ul>
   *   <li>PostgreSQL: "t"/"f" or "true"/"false"
   *   <li>MySQL: "1"/"0" or "true"/"false"
   *   <li>Standard: "true"/"false"
   * </ul>
   *
   * @param value the database boolean value as string
   * @param defaultValue the default value if parsing fails
   * @return parsed boolean value
   */
  static boolean parseDatabaseBoolean(String value, boolean defaultValue) {
    if (value == null || value.isEmpty()) {
      return defaultValue;
    }

    String normalized = value.toLowerCase().trim();
    return "t".equals(normalized) || "true".equals(normalized) || "1".equals(normalized);
  }
}
