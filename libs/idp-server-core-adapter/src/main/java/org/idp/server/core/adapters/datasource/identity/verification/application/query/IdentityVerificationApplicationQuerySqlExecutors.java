package org.idp.server.core.adapters.datasource.identity.verification.application.query;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.datasource.DatabaseType;
import org.idp.server.basic.exception.UnSupportedException;

public class IdentityVerificationApplicationQuerySqlExecutors {

  Map<DatabaseType, IdentityVerificationApplicationQuerySqlExecutor> executors;

  public IdentityVerificationApplicationQuerySqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(DatabaseType.POSTGRESQL, new PostgresqlExecutor());
  }

  public IdentityVerificationApplicationQuerySqlExecutor get(DatabaseType databaseType) {
    IdentityVerificationApplicationQuerySqlExecutor executor = executors.get(databaseType);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + databaseType.name());
    }

    return executor;
  }
}
