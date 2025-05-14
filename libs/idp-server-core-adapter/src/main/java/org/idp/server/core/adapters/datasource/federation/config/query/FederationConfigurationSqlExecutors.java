package org.idp.server.core.adapters.datasource.federation.config.query;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.datasource.DatabaseType;
import org.idp.server.basic.exception.UnSupportedException;

public class FederationConfigurationSqlExecutors {

  Map<DatabaseType, FederationConfigurationSqlExecutor> executors;

  public FederationConfigurationSqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(DatabaseType.POSTGRESQL, new PostgresqlExecutor());
    executors.put(DatabaseType.MYSQL, new MysqlExecutor());
  }

  public FederationConfigurationSqlExecutor get(DatabaseType databaseType) {
    FederationConfigurationSqlExecutor executor = executors.get(databaseType);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + databaseType.name());
    }

    return executor;
  }
}
