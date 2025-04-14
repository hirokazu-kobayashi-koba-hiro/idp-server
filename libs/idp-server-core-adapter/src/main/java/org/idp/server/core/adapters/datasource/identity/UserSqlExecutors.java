package org.idp.server.core.adapters.datasource.identity;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.basic.sql.DatabaseType;
import org.idp.server.core.type.exception.UnSupportedException;

public class UserSqlExecutors {

  Map<DatabaseType, UserSqlExecutor> executors;

  public UserSqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(DatabaseType.POSTGRESQL, new PostgresqlExecutor());
    executors.put(DatabaseType.MYSQL, new MysqlExecutor());
  }

  public UserSqlExecutor get(DatabaseType databaseType) {
    UserSqlExecutor executor = executors.get(databaseType);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + databaseType);
    }

    return executor;
  }
}
