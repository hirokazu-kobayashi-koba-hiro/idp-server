package org.idp.server.core.adapters.datasource.authentication.transaction.command;

import org.idp.server.core.authentication.AuthenticationTransaction;
import org.idp.server.core.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.tenant.Tenant;

public interface AuthenticationTransactionCommandSqlExecutor {

  void insert(Tenant tenant, AuthenticationTransaction authenticationTransaction);

  void update(Tenant tenant, AuthenticationTransaction authenticationTransaction);

  void delete(Tenant tenant, AuthenticationTransactionIdentifier identifier);
}
