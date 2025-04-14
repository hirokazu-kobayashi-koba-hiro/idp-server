package org.idp.server.core.adapters.datasource.authentication.transaction.command;

import org.idp.server.core.authentication.AuthenticationTransactionCommandRepository;
import org.idp.server.core.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.tenant.Tenant;

public class AuthenticationTransactionCommandDataSource
    implements AuthenticationTransactionCommandRepository {

  AuthenticationTransactionCommandSqlExecutors executors;
  JsonConverter jsonConverter;

  public AuthenticationTransactionCommandDataSource() {
    this.executors = new AuthenticationTransactionCommandSqlExecutors();
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  @Override
  public <T> void register(
      Tenant tenant, AuthenticationTransactionIdentifier identifier, String type, T payload) {
    AuthenticationTransactionCommandSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(identifier, type, payload);
  }

  @Override
  public <T> void update(
      Tenant tenant, AuthenticationTransactionIdentifier identifier, String type, T payload) {
    AuthenticationTransactionCommandSqlExecutor executor = executors.get(tenant.databaseType());
    executor.update(identifier, type, payload);
  }
}
