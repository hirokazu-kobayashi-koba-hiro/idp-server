package org.idp.server.core.adapters.datasource.authentication.interaction.command;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.datasource.DatabaseType;
import org.idp.server.platform.exception.UnSupportedException;

public class AuthenticationInteractionCommandSqlExecutors {

  Map<DatabaseType, AuthenticationInteractionCommandSqlExecutor> executors;

  public AuthenticationInteractionCommandSqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(DatabaseType.POSTGRESQL, new PostgresqlExecutor());
    executors.put(DatabaseType.MYSQL, new MysqlExecutor());
  }

  public AuthenticationInteractionCommandSqlExecutor get(DatabaseType databaseType) {
    AuthenticationInteractionCommandSqlExecutor executor = executors.get(databaseType);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + databaseType.name());
    }

    return executor;
  }
}
