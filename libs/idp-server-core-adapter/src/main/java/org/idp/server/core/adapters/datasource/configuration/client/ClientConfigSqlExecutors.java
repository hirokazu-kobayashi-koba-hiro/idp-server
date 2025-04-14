package org.idp.server.core.adapters.datasource.configuration.client;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.basic.sql.DatabaseType;
import org.idp.server.core.type.exception.UnSupportedException;

public class ClientConfigSqlExecutors {

  Map<DatabaseType, ClientConfigSqlExecutor> executors;

  public ClientConfigSqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(DatabaseType.POSTGRESQL, new PostgresqlExecutor());
    executors.put(DatabaseType.MYSQL, new MysqlExecutor());
  }

  public ClientConfigSqlExecutor get(DatabaseType databaseType) {
    ClientConfigSqlExecutor executor = executors.get(databaseType);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + databaseType);
    }

    return executor;
  }
}
