package org.idp.server.core.adapters.datasource.organization;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.datasource.DatabaseType;
import org.idp.server.basic.exception.UnSupportedException;

public class OrganizationSqlExecutors {

  Map<DatabaseType, OrganizationSqlExecutor> executors;

  public OrganizationSqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(DatabaseType.POSTGRESQL, new PostgresqlExecutor());
    executors.put(DatabaseType.MYSQL, new MysqlExecutor());
  }

  public OrganizationSqlExecutor get(DatabaseType databaseType) {
    OrganizationSqlExecutor executor = executors.get(databaseType);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + databaseType.name());
    }

    return executor;
  }
}
