package org.idp.server.core.adapters.datasource.security.hook;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.datasource.DatabaseType;

public class SecurityEventHoolResultSqlExecutors {

  Map<DatabaseType, SecurityEventHoolResultSqlExecutor> executors;

  public SecurityEventHoolResultSqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(DatabaseType.POSTGRESQL, new PostgresqlExecutor());
  }

  public SecurityEventHoolResultSqlExecutor get(DatabaseType databaseType) {
    SecurityEventHoolResultSqlExecutor executor = executors.get(databaseType);

    if (executor == null) {
      throw new IllegalArgumentException("Unknown dialect " + databaseType.name());
    }

    return executor;
  }
}
