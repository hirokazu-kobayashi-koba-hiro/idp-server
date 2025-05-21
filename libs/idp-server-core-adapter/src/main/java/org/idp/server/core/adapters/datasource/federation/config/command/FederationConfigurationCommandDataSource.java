package org.idp.server.core.adapters.datasource.federation.config.command;

import org.idp.server.core.federation.FederationConfiguration;
import org.idp.server.core.federation.repository.FederationConfigurationCommandRepository;
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
