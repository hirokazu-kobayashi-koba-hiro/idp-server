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

package org.idp.server.core.adapters.datasource.authentication.config.command;

import org.idp.server.core.openid.authentication.config.AuthenticationConfiguration;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationCommandRepository;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class AuthenticationConfigurationCommandDataSource
    implements AuthenticationConfigurationCommandRepository {

  AuthenticationConfigCommandSqlExecutor executor;
  JsonConverter jsonConverter;

  public AuthenticationConfigurationCommandDataSource(
      AuthenticationConfigCommandSqlExecutor executor, JsonConverter jsonConverter) {
    this.executor = executor;
    this.jsonConverter = jsonConverter;
  }

  @Override
  public void register(Tenant tenant, AuthenticationConfiguration configuration) {
    executor.insert(tenant, configuration);
  }

  @Override
  public void update(Tenant tenant, AuthenticationConfiguration configuration) {
    executor.update(tenant, configuration);
  }

  @Override
  public void delete(Tenant tenant, AuthenticationConfiguration configuration) {
    executor.delete(tenant, configuration);
  }
}
