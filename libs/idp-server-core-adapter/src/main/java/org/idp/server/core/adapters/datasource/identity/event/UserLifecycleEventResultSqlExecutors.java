package org.idp.server.core.adapters.datasource.identity.event;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.datasource.DatabaseType;
import org.idp.server.platform.exception.UnSupportedException;

public class UserLifecycleEventResultSqlExecutors {

  Map<DatabaseType, UserLifecycleEventResultSqlExecutor> executors;

  public UserLifecycleEventResultSqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(DatabaseType.POSTGRESQL, new PostgresqlExecutor());
  }

  public UserLifecycleEventResultSqlExecutor get(DatabaseType databaseType) {
    UserLifecycleEventResultSqlExecutor executor = executors.get(databaseType);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + databaseType.name());
    }

    return executor;
  }
}
