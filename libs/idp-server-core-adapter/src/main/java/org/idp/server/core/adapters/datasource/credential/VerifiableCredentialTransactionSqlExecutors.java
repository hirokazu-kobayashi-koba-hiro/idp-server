package org.idp.server.core.adapters.datasource.credential;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.datasource.DatabaseType;
import org.idp.server.basic.exception.UnSupportedException;

public class VerifiableCredentialTransactionSqlExecutors {

  Map<DatabaseType, VerifiableCredentialTransactionSqlExecutor> executors;

  public VerifiableCredentialTransactionSqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(DatabaseType.POSTGRESQL, new PostgresqlExecutor());
    executors.put(DatabaseType.MYSQL, new MysqlExecutor());
  }

  public VerifiableCredentialTransactionSqlExecutor get(DatabaseType databaseType) {
    VerifiableCredentialTransactionSqlExecutor executor = executors.get(databaseType);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + databaseType.name());
    }

    return executor;
  }
}
