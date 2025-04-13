package org.idp.server.core.adapters.datasource.federation.session.command;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.basic.sql.Dialect;
import org.idp.server.core.type.exception.UnSupportedException;

public class SsoSessionCommandSqlExecutors {

  Map<Dialect, SsoSessionCommandSqlExecutor> executors;

  public SsoSessionCommandSqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(Dialect.POSTGRESQL, new PostgresqlExecutor());
    executors.put(Dialect.MYSQL, new MysqlExecutor());
  }

  public SsoSessionCommandSqlExecutor get(Dialect dialect) {
    SsoSessionCommandSqlExecutor executor = executors.get(dialect);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + dialect);
    }

    return executor;
  }
}
