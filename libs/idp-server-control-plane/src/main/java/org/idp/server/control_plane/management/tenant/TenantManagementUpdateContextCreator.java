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

package org.idp.server.control_plane.management.tenant;

import java.util.Map;
import org.idp.server.control_plane.management.tenant.io.TenantRequest;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantAttributes;
import org.idp.server.platform.multi_tenancy.tenant.TenantDomain;
import org.idp.server.platform.multi_tenancy.tenant.config.CorsConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.config.SessionConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.config.UIConfiguration;
import org.idp.server.platform.security.event.SecurityEventUserAttributeConfiguration;
import org.idp.server.platform.security.log.SecurityEventLogConfiguration;

public class TenantManagementUpdateContextCreator {
  Tenant adminTenant;
  Tenant before;
  TenantRequest request;
  User user;
  boolean dryRun;
  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  public TenantManagementUpdateContextCreator(
      Tenant adminTenant, Tenant before, TenantRequest request, User user, boolean dryRun) {
    this.adminTenant = adminTenant;
    this.before = before;
    this.request = request;
    this.user = user;
    this.dryRun = dryRun;
  }

  public TenantManagementUpdateContext create() {
    JsonNodeWrapper jsonNodeWrapper = jsonConverter.readTree(request.toMap());
    String domain = jsonNodeWrapper.getValueOrEmptyAsString("domain");
    TenantAttributes attributes = extractConfiguration("attributes", TenantAttributes.class);
    UIConfiguration uiConfiguration = extractConfiguration("ui_config", UIConfiguration.class);
    CorsConfiguration corsConfiguration =
        extractConfiguration("cors_config", CorsConfiguration.class);
    SessionConfiguration sessionConfiguration =
        extractConfiguration("session_config", SessionConfiguration.class);
    SecurityEventLogConfiguration securityEventLogConfiguration =
        extractConfiguration("security_event_log_config", SecurityEventLogConfiguration.class);
    SecurityEventUserAttributeConfiguration securityEventUserAttributeConfiguration =
        extractConfiguration(
            "security_event_user_config", SecurityEventUserAttributeConfiguration.class);
    TenantAttributes identityPolicyConfig =
        extractConfiguration("identity_policy_config", TenantAttributes.class);

    Tenant updated =
        new Tenant(
            before.identifier(),
            before.name(),
            before.type(),
            domain.isEmpty() ? before.domain() : new TenantDomain(domain),
            before.authorizationProvider(),
            attributes.exists() ? attributes : before.attributes(),
            uiConfiguration.exists() ? uiConfiguration : before.uiConfiguration(),
            corsConfiguration.exists() ? corsConfiguration : before.corsConfiguration(),
            sessionConfiguration.exists() ? sessionConfiguration : before.sessionConfiguration(),
            securityEventLogConfiguration.exists()
                ? securityEventLogConfiguration
                : before.securityEventLogConfiguration(),
            securityEventUserAttributeConfiguration.exists()
                ? securityEventUserAttributeConfiguration
                : before.securityEventUserAttributeConfiguration(),
            identityPolicyConfig.exists() ? identityPolicyConfig : before.identityPolicyConfig());

    return new TenantManagementUpdateContext(adminTenant, before, updated, user, dryRun);
  }

  private <T> T extractConfiguration(String key, Class<T> clazz) {
    JsonNodeWrapper jsonNodeWrapper = jsonConverter.readTree(request.toMap());
    JsonNodeWrapper configNode = jsonNodeWrapper.getValueAsJsonNode(key);
    if (configNode == null || !configNode.exists()) {
      try {
        return clazz.getDeclaredConstructor().newInstance();
      } catch (Exception e) {
        throw new RuntimeException("Failed to create default configuration for " + key, e);
      }
    }
    Map<String, Object> configMap = configNode.toMap();
    try {
      return clazz.getDeclaredConstructor(Map.class).newInstance(configMap);
    } catch (Exception e) {
      throw new RuntimeException("Failed to create configuration for " + key, e);
    }
  }
}
