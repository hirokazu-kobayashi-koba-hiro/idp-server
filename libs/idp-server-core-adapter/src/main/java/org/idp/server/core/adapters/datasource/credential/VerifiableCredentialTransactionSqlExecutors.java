package org.idp.server.core.adapters.datasource.credential;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.basic.sql.Dialect;
import org.idp.server.core.type.exception.UnSupportedException;

public class VerifiableCredentialTransactionSqlExecutors {

  Map<Dialect, VerifiableCredentialTransactionSqlExecutor> executors;

  public VerifiableCredentialTransactionSqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(Dialect.POSTGRESQL, new PostgresqlExecutor());
    executors.put(Dialect.MYSQL, new MysqlExecutor());
  }

  public VerifiableCredentialTransactionSqlExecutor get(Dialect dialect) {
    VerifiableCredentialTransactionSqlExecutor executor = executors.get(dialect);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + dialect);
    }

    return executor;
  }
}
