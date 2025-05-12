package org.idp.server.core.adapters.datasource.authentication.config.command;

import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.adapters.datasource.authentication.config.query.AuthenticationConfigSqlExecutor;
import org.idp.server.core.adapters.datasource.authentication.config.query.AuthenticationConfigSqlExecutors;
import org.idp.server.core.authentication.AuthenticationConfiguration;
import org.idp.server.core.authentication.exception.AuthenticationConfigurationNotFoundException;
import org.idp.server.core.authentication.repository.AuthenticationConfigurationCommandRepository;
import org.idp.server.core.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

import java.util.Map;
import java.util.Objects;

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
}
