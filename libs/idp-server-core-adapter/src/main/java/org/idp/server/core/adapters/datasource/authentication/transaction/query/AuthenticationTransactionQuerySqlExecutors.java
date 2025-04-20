package org.idp.server.core.adapters.datasource.authentication.transaction.query;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.basic.datasource.DatabaseType;
import org.idp.server.core.type.exception.UnSupportedException;

public class AuthenticationTransactionQuerySqlExecutors {

  Map<DatabaseType, AuthenticationTransactionQuerySqlExecutor> executors;

  public AuthenticationTransactionQuerySqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(DatabaseType.POSTGRESQL, new PostgresqlExecutor());
    //    executors.put(DatabaseType.MYSQL, new MysqlExecutor());
  }

  public AuthenticationTransactionQuerySqlExecutor get(DatabaseType databaseType) {
    AuthenticationTransactionQuerySqlExecutor executor = executors.get(databaseType);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + databaseType);
    }

    return executor;
  }
}
