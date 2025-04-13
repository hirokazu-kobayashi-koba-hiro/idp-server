package org.idp.server.core.adapters.datasource.oauth.request;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.basic.sql.Dialect;
import org.idp.server.core.type.exception.UnSupportedException;

public class AuthorizationRequestSqlExecutors {

  Map<Dialect, AuthorizationRequestSqlExecutor> executors;

  public AuthorizationRequestSqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(Dialect.POSTGRESQL, new PostgresqlSqlExecutor());
    executors.put(Dialect.MYSQL, new MysqlSqlExecutor());
  }

  public AuthorizationRequestSqlExecutor get(Dialect dialect) {
    AuthorizationRequestSqlExecutor executor = executors.get(dialect);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + dialect);
    }

    return executor;
  }
}
