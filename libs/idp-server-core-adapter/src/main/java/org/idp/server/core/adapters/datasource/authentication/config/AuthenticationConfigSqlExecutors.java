package org.idp.server.core.adapters.datasource.authentication.config;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.datasource.DatabaseType;
import org.idp.server.basic.exception.UnSupportedException;

public class AuthenticationConfigSqlExecutors {

  Map<DatabaseType, AuthenticationConfigSqlExecutor> executors;

  public AuthenticationConfigSqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(DatabaseType.POSTGRESQL, new PostgresqlExecutor());
    executors.put(DatabaseType.MYSQL, new MysqlExecutor());
  }

  public AuthenticationConfigSqlExecutor get(DatabaseType databaseType) {
    AuthenticationConfigSqlExecutor executor = executors.get(databaseType);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + databaseType.name());
    }

    return executor;
  }
}
