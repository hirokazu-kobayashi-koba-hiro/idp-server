package org.idp.server.core.adapters.datasource.token;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.basic.sql.DatabaseType;
import org.idp.server.core.type.exception.UnSupportedException;

public class OAuthTokenSqlExecutors {

  Map<DatabaseType, OAuthTokenSqlExecutor> executors;

  public OAuthTokenSqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(DatabaseType.POSTGRESQL, new PostgresqlExecutor());
    executors.put(DatabaseType.MYSQL, new MysqlExecutor());
  }

  public OAuthTokenSqlExecutor get(DatabaseType databaseType) {
    OAuthTokenSqlExecutor executor = executors.get(databaseType);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + databaseType);
    }

    return executor;
  }
}
