package org.idp.server.core.adapters.datasource.security;

import org.idp.server.core.basic.sql.Dialect;
import org.idp.server.core.type.exception.UnSupportedException;

import java.util.HashMap;
import java.util.Map;

public class SecurityEventSqlExecutors {

  Map<Dialect, SecurityEventSqlExecutor> executors;

  public SecurityEventSqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(Dialect.POSTGRESQL, new PostgresqlExecutor());
    executors.put(Dialect.MYSQL, new MysqlExecutor());
  }

  public SecurityEventSqlExecutor get(Dialect dialect) {
    SecurityEventSqlExecutor executor = executors.get(dialect);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + dialect);
    }

    return executor;
  }
}
