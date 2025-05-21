package org.idp.server.core.adapters.datasource.security.hook.configuration.command;

import org.idp.server.basic.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.hook.SecurityEventHookConfiguration;
import org.idp.server.platform.security.repository.SecurityEventHookConfigurationCommandRepository;

public class SecurityEventHookConfigurationCommandDataSource
    implements SecurityEventHookConfigurationCommandRepository {

  SecurityEventHookConfigSqlExecutors executors;
  JsonConverter converter;

  public SecurityEventHookConfigurationCommandDataSource() {
    this.executors = new SecurityEventHookConfigSqlExecutors();
    this.converter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public void register(Tenant tenant, SecurityEventHookConfiguration configuration) {
    SecurityEventHookConfigSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(tenant, configuration);
  }

  @Override
  public void update(Tenant tenant, SecurityEventHookConfiguration configuration) {
    SecurityEventHookConfigSqlExecutor executor = executors.get(tenant.databaseType());
    executor.update(tenant, configuration);
  }

  @Override
  public void delete(Tenant tenant, SecurityEventHookConfiguration configuration) {
    SecurityEventHookConfigSqlExecutor executor = executors.get(tenant.databaseType());
    executor.delete(tenant, configuration);
  }
}
