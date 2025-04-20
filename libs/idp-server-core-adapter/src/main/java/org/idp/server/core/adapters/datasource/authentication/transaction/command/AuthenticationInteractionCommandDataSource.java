package org.idp.server.core.adapters.datasource.authentication.transaction.command;

import org.idp.server.core.authentication.AuthenticationInteractionCommandRepository;
import org.idp.server.core.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.tenant.Tenant;

public class AuthenticationInteractionCommandDataSource
    implements AuthenticationInteractionCommandRepository {

  AuthenticationTransactionCommandSqlExecutors executors;
  JsonConverter jsonConverter;

  public AuthenticationInteractionCommandDataSource() {
    this.executors = new AuthenticationTransactionCommandSqlExecutors();
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  @Override
  public <T> void register(
      Tenant tenant, AuthenticationTransactionIdentifier identifier, String type, T payload) {
    AuthenticationTransactionCommandSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(tenant, identifier, type, payload);
  }

  @Override
  public <T> void update(
      Tenant tenant, AuthenticationTransactionIdentifier identifier, String type, T payload) {
    AuthenticationTransactionCommandSqlExecutor executor = executors.get(tenant.databaseType());
    executor.update(tenant, identifier, type, payload);
  }
}
