package org.idp.server.core.adapters.datasource.federation.session.query;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.basic.sql.DatabaseType;
import org.idp.server.core.type.exception.UnSupportedException;

public class SsoSessionQuerySqlExecutors {

  Map<DatabaseType, SsoSessionQuerySqlExecutor> executors;

  public SsoSessionQuerySqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(DatabaseType.POSTGRESQL, new PostgresqlExecutor());
    executors.put(DatabaseType.MYSQL, new MysqlExecutor());
  }

  public SsoSessionQuerySqlExecutor get(DatabaseType databaseType) {
    SsoSessionQuerySqlExecutor executor = executors.get(databaseType);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + databaseType);
    }

    return executor;
  }
}
