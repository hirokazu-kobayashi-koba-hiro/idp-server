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

package org.idp.server.core.adapters.datasource.security.hook.configuration.query;

import java.util.List;
import java.util.Map;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfiguration;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfigurationIdentifier;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfigurations;
import org.idp.server.platform.security.repository.SecurityEventHookConfigurationQueryRepository;

public class SecurityEventHookConfigurationQueryDataSource
    implements SecurityEventHookConfigurationQueryRepository {

  SecurityEventHookConfigSqlExecutor executor;
  JsonConverter jsonConverter;

  public SecurityEventHookConfigurationQueryDataSource(
      SecurityEventHookConfigSqlExecutor executor) {
    this.executor = executor;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public SecurityEventHookConfigurations find(Tenant tenant) {
    List<Map<String, String>> results = executor.selectListBy(tenant);

    if (results == null || results.isEmpty()) {
      return new SecurityEventHookConfigurations();
    }

    List<SecurityEventHookConfiguration> list =
        results.stream()
            .map(
                result ->
                    jsonConverter.read(result.get("payload"), SecurityEventHookConfiguration.class))
            .toList();

    return new SecurityEventHookConfigurations(list);
  }

  @Override
  public SecurityEventHookConfiguration find(
      Tenant tenant, SecurityEventHookConfigurationIdentifier identifier) {
    Map<String, String> result = executor.selectOne(tenant, identifier);

    if (result == null || result.isEmpty()) {
      return new SecurityEventHookConfiguration();
    }

    return jsonConverter.read(result.get("payload"), SecurityEventHookConfiguration.class);
  }

  @Override
  public SecurityEventHookConfiguration findWithDisabled(
      Tenant tenant, SecurityEventHookConfigurationIdentifier identifier, boolean includeDisabled) {
    Map<String, String> result = executor.selectOne(tenant, identifier, includeDisabled);

    if (result == null || result.isEmpty()) {
      return new SecurityEventHookConfiguration();
    }

    return jsonConverter.read(result.get("payload"), SecurityEventHookConfiguration.class);
  }

  @Override
  public SecurityEventHookConfiguration find(Tenant tenant, String type) {
    Map<String, String> result = executor.selectOne(tenant, type);

    if (result == null || result.isEmpty()) {
      return new SecurityEventHookConfiguration();
    }

    return jsonConverter.read(result.get("payload"), SecurityEventHookConfiguration.class);
  }

  @Override
  public long findTotalCount(Tenant tenant) {
    Map<String, String> result = executor.selectCount(tenant);
    if (result == null || result.isEmpty()) {
      return 0L;
    }
    return Long.parseLong(result.get("count"));
  }

  @Override
  public List<SecurityEventHookConfiguration> findList(Tenant tenant, int limit, int offset) {
    List<Map<String, String>> results = executor.selectList(tenant, limit, offset);

    if (results == null || results.isEmpty()) {
      return List.of();
    }

    return results.stream()
        .map(
            result ->
                jsonConverter.read(result.get("payload"), SecurityEventHookConfiguration.class))
        .toList();
  }
}
