package org.idp.server.core.adapters.datasource.identity.command;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.datasource.DatabaseType;
import org.idp.server.basic.exception.UnSupportedException;

public class UserCommandSqlExecutors {

  Map<DatabaseType, UserCommandSqlExecutor> executors;

  public UserCommandSqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(DatabaseType.POSTGRESQL, new PostgresqlExecutor());
    //    executors.put(DatabaseType.MYSQL, new MysqlExecutor());
  }

  public UserCommandSqlExecutor get(DatabaseType databaseType) {
    UserCommandSqlExecutor executor = executors.get(databaseType);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + databaseType.name());
    }

    return executor;
  }
}
