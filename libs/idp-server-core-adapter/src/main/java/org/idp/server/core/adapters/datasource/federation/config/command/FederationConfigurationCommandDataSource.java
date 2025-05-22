/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.federation.config.command;

import org.idp.server.core.oidc.federation.FederationConfiguration;
import org.idp.server.core.oidc.federation.repository.FederationConfigurationCommandRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class FederationConfigurationCommandDataSource
    implements FederationConfigurationCommandRepository {

  FederationConfigurationSqlExecutors executors;

  public FederationConfigurationCommandDataSource() {
    this.executors = new FederationConfigurationSqlExecutors();
  }

  @Override
  public void register(Tenant tenant, FederationConfiguration configuration) {
    FederationConfigurationSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(tenant, configuration);
  }

  @Override
  public void update(Tenant tenant, FederationConfiguration configuration) {
    FederationConfigurationSqlExecutor executor = executors.get(tenant.databaseType());
    executor.update(tenant, configuration);
  }

  @Override
  public void delete(Tenant tenant, FederationConfiguration configuration) {
    FederationConfigurationSqlExecutor executor = executors.get(tenant.databaseType());
    executor.delete(tenant, configuration);
  }
}
