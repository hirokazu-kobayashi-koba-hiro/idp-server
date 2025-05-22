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
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.hook.SecurityEventHookConfiguration;
import org.idp.server.platform.security.hook.SecurityEventHookConfigurationIdentifier;
import org.idp.server.platform.security.hook.SecurityEventHookConfigurations;
import org.idp.server.platform.security.repository.SecurityEventHookConfigurationQueryRepository;

public class SecurityEventHookConfigurationQueryDataSource
    implements SecurityEventHookConfigurationQueryRepository {

  SecurityEventHookConfigSqlExecutors executors;

  public SecurityEventHookConfigurationQueryDataSource() {
    this.executors = new SecurityEventHookConfigSqlExecutors();
  }

  @Override
  public SecurityEventHookConfigurations find(Tenant tenant) {
    SecurityEventHookConfigSqlExecutor executor = executors.get(tenant.databaseType());
    List<Map<String, String>> results = executor.selectListBy(tenant);

    if (results == null || results.isEmpty()) {
      return new SecurityEventHookConfigurations();
    }

    List<SecurityEventHookConfiguration> list =
        results.stream().map(ModelConverter::convert).toList();

    return new SecurityEventHookConfigurations(list);
  }

  @Override
  public SecurityEventHookConfiguration find(
      Tenant tenant, SecurityEventHookConfigurationIdentifier identifier) {
    SecurityEventHookConfigSqlExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> result = executor.selectOne(tenant, identifier);

    if (result == null || result.isEmpty()) {
      return new SecurityEventHookConfiguration();
    }

    return ModelConverter.convert(result);
  }

  @Override
  public List<SecurityEventHookConfiguration> findList(Tenant tenant, int limit, int offset) {
    SecurityEventHookConfigSqlExecutor executor = executors.get(tenant.databaseType());
    List<Map<String, String>> results = executor.selectList(tenant, limit, offset);

    if (results == null || results.isEmpty()) {
      return List.of();
    }

    return results.stream().map(ModelConverter::convert).toList();
  }
}
