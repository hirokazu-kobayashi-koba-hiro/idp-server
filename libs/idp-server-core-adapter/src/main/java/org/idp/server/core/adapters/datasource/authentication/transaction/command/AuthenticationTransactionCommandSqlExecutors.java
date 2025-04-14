package org.idp.server.core.adapters.datasource.authentication.transaction.command;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.basic.datasource.DatabaseType;
import org.idp.server.core.type.exception.UnSupportedException;

public class AuthenticationTransactionCommandSqlExecutors {

  Map<DatabaseType, AuthenticationTransactionCommandSqlExecutor> executors;

  public AuthenticationTransactionCommandSqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(DatabaseType.POSTGRESQL, new PostgresqlExecutor());
    executors.put(DatabaseType.MYSQL, new MysqlExecutor());
  }

  public AuthenticationTransactionCommandSqlExecutor get(DatabaseType databaseType) {
    AuthenticationTransactionCommandSqlExecutor executor = executors.get(databaseType);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + databaseType);
    }

    return executor;
  }
}
