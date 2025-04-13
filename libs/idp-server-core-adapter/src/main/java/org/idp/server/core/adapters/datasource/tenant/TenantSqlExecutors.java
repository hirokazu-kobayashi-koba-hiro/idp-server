package org.idp.server.core.adapters.datasource.tenant;


import org.idp.server.core.basic.sql.Dialect;
import org.idp.server.core.type.exception.UnSupportedException;

import java.util.HashMap;
import java.util.Map;

public class TenantSqlExecutors {

  Map<Dialect, TenantSqlExecutor> executors;

  public TenantSqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(Dialect.POSTGRESQL, new PostgresqlExecutor());
    executors.put(Dialect.MYSQL, new MysqlExecutor());
  }

  public TenantSqlExecutor get(Dialect dialect) {
    TenantSqlExecutor executor = executors.get(dialect);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + dialect);
    }

    return executor;
  }
}
