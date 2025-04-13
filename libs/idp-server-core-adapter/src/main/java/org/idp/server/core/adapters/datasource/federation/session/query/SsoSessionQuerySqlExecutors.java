package org.idp.server.core.adapters.datasource.federation.session.query;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.basic.sql.Dialect;
import org.idp.server.core.type.exception.UnSupportedException;

public class SsoSessionQuerySqlExecutors {

  Map<Dialect, SsoSessionQuerySqlExecutor> executors;

  public SsoSessionQuerySqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(Dialect.POSTGRESQL, new PostgresqlExecutor());
    executors.put(Dialect.MYSQL, new MysqlExecutor());
  }

  public SsoSessionQuerySqlExecutor get(Dialect dialect) {
    SsoSessionQuerySqlExecutor executor = executors.get(dialect);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + dialect);
    }

    return executor;
  }
}
