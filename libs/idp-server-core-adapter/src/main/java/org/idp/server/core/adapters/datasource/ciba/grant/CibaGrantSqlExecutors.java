package org.idp.server.core.adapters.datasource.ciba.grant;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.datasource.DatabaseType;
import org.idp.server.basic.exception.UnSupportedException;

public class CibaGrantSqlExecutors {

  Map<DatabaseType, CibaGrantSqlExecutor> executors;

  public CibaGrantSqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(DatabaseType.POSTGRESQL, new PostgresqlExecutor());
    executors.put(DatabaseType.MYSQL, new MysqlExecutor());
  }

  public CibaGrantSqlExecutor get(DatabaseType databaseType) {
    CibaGrantSqlExecutor executor = executors.get(databaseType);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + databaseType.name());
    }

    return executor;
  }
}
