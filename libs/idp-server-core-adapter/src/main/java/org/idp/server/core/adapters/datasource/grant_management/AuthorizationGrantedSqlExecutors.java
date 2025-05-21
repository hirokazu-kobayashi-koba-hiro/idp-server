package org.idp.server.core.adapters.datasource.grant_management;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.datasource.DatabaseType;
import org.idp.server.platform.exception.UnSupportedException;

public class AuthorizationGrantedSqlExecutors {

  Map<DatabaseType, AuthorizationGrantedSqlExecutor> executors;

  public AuthorizationGrantedSqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(DatabaseType.POSTGRESQL, new PostgresqlExecutor());
    executors.put(DatabaseType.MYSQL, new MysqlExecutor());
  }

  public AuthorizationGrantedSqlExecutor get(DatabaseType databaseType) {
    AuthorizationGrantedSqlExecutor executor = executors.get(databaseType);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + databaseType.name());
    }

    return executor;
  }
}
