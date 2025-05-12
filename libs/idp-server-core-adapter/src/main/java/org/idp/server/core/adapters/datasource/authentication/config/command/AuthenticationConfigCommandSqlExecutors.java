package org.idp.server.core.adapters.datasource.authentication.config.command;

import org.idp.server.basic.datasource.DatabaseType;
import org.idp.server.basic.exception.UnSupportedException;
import java.util.HashMap;
import java.util.Map;

public class AuthenticationConfigCommandSqlExecutors {

  Map<DatabaseType, AuthenticationConfigCommandSqlExecutor> executors;

  public AuthenticationConfigCommandSqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(DatabaseType.POSTGRESQL, new PostgresqlExecutor());
    executors.put(DatabaseType.MYSQL, new MysqlExecutor());
  }

  public AuthenticationConfigCommandSqlExecutor get(DatabaseType databaseType) {
    AuthenticationConfigCommandSqlExecutor executor = executors.get(databaseType);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + databaseType.name());
    }

    return executor;
  }
}
