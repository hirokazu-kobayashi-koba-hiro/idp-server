package org.idp.server.core.adapters.datasource.authentication.config;

import org.idp.server.core.basic.sql.Dialect;
import org.idp.server.core.type.exception.UnSupportedException;

import java.util.HashMap;
import java.util.Map;

public class AuthenticationConfigSqlExecutors {

  Map<Dialect, AuthenticationConfigSqlExecutor> executors;

  public AuthenticationConfigSqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(Dialect.POSTGRESQL, new PostgresqlExecutor());
    executors.put(Dialect.MYSQL, new MysqlExecutor());
  }

  public AuthenticationConfigSqlExecutor get(Dialect dialect) {
    AuthenticationConfigSqlExecutor executor = executors.get(dialect);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + dialect);
    }

    return executor;
  }
}
