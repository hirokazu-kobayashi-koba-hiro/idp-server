package org.idp.server.core.adapters.datasource.authentication.config.command;

import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.authentication.AuthenticationConfiguration;
import org.idp.server.core.authentication.repository.AuthenticationConfigurationCommandRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class AuthenticationConfigurationCommandDataSource
    implements AuthenticationConfigurationCommandRepository {

  AuthenticationConfigCommandSqlExecutors executors;
  JsonConverter jsonConverter;

  public AuthenticationConfigurationCommandDataSource() {
    this.executors = new AuthenticationConfigCommandSqlExecutors();
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public void register(Tenant tenant, AuthenticationConfiguration configuration) {
    AuthenticationConfigCommandSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(tenant, configuration);
  }

  @Override
  public void update(Tenant tenant, AuthenticationConfiguration configuration) {
    AuthenticationConfigCommandSqlExecutor executor = executors.get(tenant.databaseType());
    executor.update(tenant, configuration);
  }

  @Override
  public void delete(Tenant tenant, AuthenticationConfiguration configuration) {
    AuthenticationConfigCommandSqlExecutor executor = executors.get(tenant.databaseType());
    executor.delete(tenant, configuration);
  }
}
