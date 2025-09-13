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

package org.idp.server.core.adapters.datasource.identity.verification.config.command;

import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationConfigurationCommandRepository;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class IdentityVerificationConfigurationCommandDataSource
    implements IdentityVerificationConfigurationCommandRepository {

  IdentityVerificationConfigCommandSqlExecutor executor;
  JsonConverter jsonConverter;

  public IdentityVerificationConfigurationCommandDataSource(
      IdentityVerificationConfigCommandSqlExecutor executor, JsonConverter jsonConverter) {
    this.executor = executor;
    this.jsonConverter = jsonConverter;
  }

  @Override
  public void register(
      Tenant tenant,
      IdentityVerificationType type,
      IdentityVerificationConfiguration configuration) {
    executor.insert(tenant, type, configuration);
  }

  @Override
  public void update(
      Tenant tenant,
      IdentityVerificationType type,
      IdentityVerificationConfiguration configuration) {
    executor.update(tenant, type, configuration);
  }

  @Override
  public void delete(
      Tenant tenant,
      IdentityVerificationType type,
      IdentityVerificationConfiguration configuration) {
    executor.delete(tenant, type, configuration);
  }
}
