package org.idp.server.core.adapters.datasource.authentication.interaction.query;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.basic.datasource.DatabaseType;
import org.idp.server.core.type.exception.UnSupportedException;

public class AuthenticationInteractionQuerySqlExecutors {

  Map<DatabaseType, AuthenticationInteractionQuerySqlExecutor> executors;

  public AuthenticationInteractionQuerySqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(DatabaseType.POSTGRESQL, new PostgresqlExecutor());
    executors.put(DatabaseType.MYSQL, new MysqlExecutor());
  }

  public AuthenticationInteractionQuerySqlExecutor get(DatabaseType databaseType) {
    AuthenticationInteractionQuerySqlExecutor executor = executors.get(databaseType);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + databaseType);
    }

    return executor;
  }
}
