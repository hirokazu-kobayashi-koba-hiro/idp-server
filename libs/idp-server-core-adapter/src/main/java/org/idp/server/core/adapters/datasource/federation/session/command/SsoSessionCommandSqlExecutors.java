package org.idp.server.core.adapters.datasource.federation.session.command;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.datasource.DatabaseType;
import org.idp.server.basic.exception.UnSupportedException;

public class SsoSessionCommandSqlExecutors {

  Map<DatabaseType, SsoSessionCommandSqlExecutor> executors;

  public SsoSessionCommandSqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(DatabaseType.POSTGRESQL, new PostgresqlExecutor());
    executors.put(DatabaseType.MYSQL, new MysqlExecutor());
  }

  public SsoSessionCommandSqlExecutor get(DatabaseType databaseType) {
    SsoSessionCommandSqlExecutor executor = executors.get(databaseType);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + databaseType.name());
    }

    return executor;
  }
}
