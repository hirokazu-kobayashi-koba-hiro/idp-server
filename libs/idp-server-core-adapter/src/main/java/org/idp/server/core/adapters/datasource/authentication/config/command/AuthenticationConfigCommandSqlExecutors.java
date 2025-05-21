package org.idp.server.core.adapters.datasource.authentication.config.command;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.datasource.DatabaseType;
import org.idp.server.platform.exception.UnSupportedException;

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
