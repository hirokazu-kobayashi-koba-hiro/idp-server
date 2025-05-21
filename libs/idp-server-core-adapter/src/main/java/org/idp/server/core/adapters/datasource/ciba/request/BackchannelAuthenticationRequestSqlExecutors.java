package org.idp.server.core.adapters.datasource.ciba.request;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.datasource.DatabaseType;
import org.idp.server.platform.exception.UnSupportedException;

public class BackchannelAuthenticationRequestSqlExecutors {

  Map<DatabaseType, BackchannelAuthenticationRequestSqlExecutor> executors;

  public BackchannelAuthenticationRequestSqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(DatabaseType.POSTGRESQL, new PostgresqlExecutor());
    executors.put(DatabaseType.MYSQL, new MysqlExecutor());
  }

  public BackchannelAuthenticationRequestSqlExecutor get(DatabaseType databaseType) {
    BackchannelAuthenticationRequestSqlExecutor executor = executors.get(databaseType);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + databaseType.name());
    }

    return executor;
  }
}
