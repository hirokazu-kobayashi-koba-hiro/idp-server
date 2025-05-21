package org.idp.server.core.adapters.datasource.security.event;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.datasource.DatabaseType;
import org.idp.server.platform.exception.UnSupportedException;

public class SecurityEventSqlExecutors {

  Map<DatabaseType, SecurityEventSqlExecutor> executors;

  public SecurityEventSqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(DatabaseType.POSTGRESQL, new PostgresqlExecutor());
    executors.put(DatabaseType.MYSQL, new MysqlExecutor());
  }

  public SecurityEventSqlExecutor get(DatabaseType databaseType) {
    SecurityEventSqlExecutor executor = executors.get(databaseType);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + databaseType.name());
    }

    return executor;
  }
}
