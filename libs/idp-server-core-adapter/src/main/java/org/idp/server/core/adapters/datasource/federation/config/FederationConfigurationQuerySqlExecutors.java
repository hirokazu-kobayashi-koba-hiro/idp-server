package org.idp.server.core.adapters.datasource.federation.config;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.basic.sql.Dialect;
import org.idp.server.core.type.exception.UnSupportedException;

public class FederationConfigurationQuerySqlExecutors {

  Map<Dialect, FederationConfigurationQuerySqlExecutor> executors;

  public FederationConfigurationQuerySqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(Dialect.POSTGRESQL, new PostgresqlExecutor());
    executors.put(Dialect.MYSQL, new MysqlExecutor());
  }

  public FederationConfigurationQuerySqlExecutor get(Dialect dialect) {
    FederationConfigurationQuerySqlExecutor executor = executors.get(dialect);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + dialect);
    }

    return executor;
  }
}
