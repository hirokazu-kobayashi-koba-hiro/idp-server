package org.idp.server.core.adapters.datasource.authentication.transaction.command;

import org.idp.server.core.authentication.AuthenticationTransaction;
import org.idp.server.core.authentication.AuthenticationTransactionCommandRepository;
import org.idp.server.core.authentication.AuthorizationIdentifier;
import org.idp.server.core.tenant.Tenant;

public class AuthenticationTransactionCommandDataSource
    implements AuthenticationTransactionCommandRepository {

  AuthenticationTransactionCommandSqlExecutors executors;

  public AuthenticationTransactionCommandDataSource() {
    this.executors = new AuthenticationTransactionCommandSqlExecutors();
  }

  @Override
  public void register(Tenant tenant, AuthenticationTransaction authenticationTransaction) {
    AuthenticationTransactionCommandSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(tenant, authenticationTransaction);
  }

  @Override
  public void update(Tenant tenant, AuthenticationTransaction authenticationTransaction) {
    AuthenticationTransactionCommandSqlExecutor executor = executors.get(tenant.databaseType());
    executor.update(tenant, authenticationTransaction);
  }

  @Override
  public void delete(Tenant tenant, AuthorizationIdentifier identifier) {
    AuthenticationTransactionCommandSqlExecutor executor = executors.get(tenant.databaseType());
    executor.delete(tenant, identifier);
  }
}
