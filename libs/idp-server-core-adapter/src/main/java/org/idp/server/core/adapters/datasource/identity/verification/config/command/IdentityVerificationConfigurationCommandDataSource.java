/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.identity.verification.config.command;

import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfigurationCommandRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class IdentityVerificationConfigurationCommandDataSource
    implements IdentityVerificationConfigurationCommandRepository {

  IdentityVerificationConfigCommandSqlExecutors executors;
  JsonConverter jsonConverter;

  public IdentityVerificationConfigurationCommandDataSource() {
    this.executors = new IdentityVerificationConfigCommandSqlExecutors();
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public void register(
      Tenant tenant,
      IdentityVerificationType type,
      IdentityVerificationConfiguration configuration) {
    IdentityVerificationConfigCommandSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(tenant, type, configuration);
  }

  @Override
  public void update(
      Tenant tenant,
      IdentityVerificationType type,
      IdentityVerificationConfiguration configuration) {
    IdentityVerificationConfigCommandSqlExecutor executor = executors.get(tenant.databaseType());
    executor.update(tenant, type, configuration);
  }

  @Override
  public void delete(
      Tenant tenant,
      IdentityVerificationType type,
      IdentityVerificationConfiguration configuration) {
    IdentityVerificationConfigCommandSqlExecutor executor = executors.get(tenant.databaseType());
    executor.delete(tenant, type, configuration);
  }
}
