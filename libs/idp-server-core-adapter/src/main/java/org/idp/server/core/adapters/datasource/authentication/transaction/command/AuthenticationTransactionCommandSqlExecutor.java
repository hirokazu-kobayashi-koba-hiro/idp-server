package org.idp.server.core.adapters.datasource.authentication.transaction.command;

import org.idp.server.core.authentication.AuthenticationTransactionIdentifier;

public interface AuthenticationTransactionCommandSqlExecutor {

  <T> void insert(AuthenticationTransactionIdentifier identifier, String key, T payload);

  <T> void update(AuthenticationTransactionIdentifier identifier, String key, T payload);
}
