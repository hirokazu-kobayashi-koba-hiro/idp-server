package org.idp.server.core.adapters.datasource.oauth.code;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.basic.sql.DatabaseType;
import org.idp.server.core.type.exception.UnSupportedException;

public class AuthorizationCodeGrantExecutors {

  Map<DatabaseType, AuthorizationCodeGrantExecutor> executors;

  public AuthorizationCodeGrantExecutors() {
    this.executors = new HashMap<>();
    executors.put(DatabaseType.POSTGRESQL, new PostgresqlExecutor());
    executors.put(DatabaseType.MYSQL, new MysqlExecutor());
  }

  public AuthorizationCodeGrantExecutor get(DatabaseType databaseType) {
    AuthorizationCodeGrantExecutor executor = executors.get(databaseType);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + databaseType);
    }

    return executor;
  }
}
