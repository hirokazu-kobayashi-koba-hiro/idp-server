package org.idp.server.core.adapters.datasource.oauth.request;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.basic.datasource.DatabaseType;
import org.idp.server.core.type.exception.UnSupportedException;

public class AuthorizationRequestSqlExecutors {

  Map<DatabaseType, AuthorizationRequestSqlExecutor> executors;

  public AuthorizationRequestSqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(DatabaseType.POSTGRESQL, new PostgresqlSqlExecutor());
    executors.put(DatabaseType.MYSQL, new MysqlSqlExecutor());
  }

  public AuthorizationRequestSqlExecutor get(DatabaseType databaseType) {
    AuthorizationRequestSqlExecutor executor = executors.get(databaseType);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + databaseType);
    }

    return executor;
  }
}
