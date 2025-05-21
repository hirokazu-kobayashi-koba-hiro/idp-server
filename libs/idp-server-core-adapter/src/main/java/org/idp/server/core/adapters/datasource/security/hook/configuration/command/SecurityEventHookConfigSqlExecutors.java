package org.idp.server.core.adapters.datasource.security.hook.configuration.command;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.datasource.DatabaseType;
import org.idp.server.platform.exception.UnSupportedException;

public class SecurityEventHookConfigSqlExecutors {

  Map<DatabaseType, SecurityEventHookConfigSqlExecutor> executors;

  public SecurityEventHookConfigSqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(DatabaseType.POSTGRESQL, new PostgresqlExecutor());
    executors.put(DatabaseType.MYSQL, new MysqlExecutor());
  }

  public SecurityEventHookConfigSqlExecutor get(DatabaseType databaseType) {
    SecurityEventHookConfigSqlExecutor executor = executors.get(databaseType);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + databaseType.name());
    }

    return executor;
  }
}
