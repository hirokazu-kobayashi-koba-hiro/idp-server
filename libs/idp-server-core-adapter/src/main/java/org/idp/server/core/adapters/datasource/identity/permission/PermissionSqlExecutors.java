package org.idp.server.core.adapters.datasource.identity.permission;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.basic.sql.Dialect;
import org.idp.server.core.type.exception.UnSupportedException;

public class PermissionSqlExecutors {

  Map<Dialect, PermissionSqlExecutor> executors;

  public PermissionSqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(Dialect.POSTGRESQL, new PostgresqlExecutor());
    executors.put(Dialect.MYSQL, new MysqlExecutor());
  }

  public PermissionSqlExecutor get(Dialect dialect) {
    PermissionSqlExecutor executor = executors.get(dialect);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + dialect);
    }

    return executor;
  }
}
