package org.idp.server.core.adapters.datasource.multi_tenancy.tenant.command;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.datasource.DatabaseType;
import org.idp.server.platform.exception.UnSupportedException;

public class TenantCommandSqlExecutors {

  Map<DatabaseType, TenantCommandSqlExecutor> executors;

  public TenantCommandSqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(DatabaseType.POSTGRESQL, new PostgresqlExecutor());
    executors.put(DatabaseType.MYSQL, new MysqlExecutor());
  }

  public TenantCommandSqlExecutor get(DatabaseType databaseType) {
    TenantCommandSqlExecutor executor = executors.get(databaseType);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + databaseType.name());
    }

    return executor;
  }
}
