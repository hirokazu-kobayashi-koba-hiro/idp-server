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

package org.idp.server.core.adapters.datasource.security.hook.configuration.command;

import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfiguration;
import org.idp.server.platform.security.repository.SecurityEventHookConfigurationCommandRepository;

public class SecurityEventHookConfigurationCommandDataSource
    implements SecurityEventHookConfigurationCommandRepository {

  SecurityEventHookConfigSqlExecutor executor;
  JsonConverter converter;

  public SecurityEventHookConfigurationCommandDataSource(
      SecurityEventHookConfigSqlExecutor executor) {
    this.executor = executor;
    this.converter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public void register(Tenant tenant, SecurityEventHookConfiguration configuration) {
    executor.insert(tenant, configuration);
  }

  @Override
  public void update(Tenant tenant, SecurityEventHookConfiguration configuration) {
    executor.update(tenant, configuration);
  }

  @Override
  public void delete(Tenant tenant, SecurityEventHookConfiguration configuration) {
    executor.delete(tenant, configuration);
  }
}
