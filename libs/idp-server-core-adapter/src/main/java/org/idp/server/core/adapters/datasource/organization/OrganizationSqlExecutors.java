package org.idp.server.core.adapters.datasource.organization;

import org.idp.server.core.basic.sql.Dialect;
import org.idp.server.core.type.exception.UnSupportedException;

import java.util.HashMap;
import java.util.Map;

public class OrganizationSqlExecutors {

  Map<Dialect, OrganizationSqlExecutor> executors;

  public OrganizationSqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(Dialect.POSTGRESQL, new PostgresqlExecutor());
    executors.put(Dialect.MYSQL, new MysqlExecutor());
  }

  public OrganizationSqlExecutor get(Dialect dialect) {
    OrganizationSqlExecutor executor = executors.get(dialect);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + dialect);
    }

    return executor;
  }
}
