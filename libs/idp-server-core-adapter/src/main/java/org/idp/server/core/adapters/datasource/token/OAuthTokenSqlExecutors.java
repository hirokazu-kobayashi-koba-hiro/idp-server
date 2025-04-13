package org.idp.server.core.adapters.datasource.token;


import org.idp.server.core.basic.sql.Dialect;
import org.idp.server.core.type.exception.UnSupportedException;

import java.util.HashMap;
import java.util.Map;

public class OAuthTokenSqlExecutors {

  Map<Dialect, OAuthTokenSqlExecutor> executors;

  public OAuthTokenSqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(Dialect.POSTGRESQL, new PostgresqlExecutor());
    executors.put(Dialect.MYSQL, new MysqlExecutor());
  }

  public OAuthTokenSqlExecutor get(Dialect dialect) {
    OAuthTokenSqlExecutor executor = executors.get(dialect);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + dialect);
    }

    return executor;
  }
}
