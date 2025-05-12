package org.idp.server.core.adapters.datasource.identity.verification.config.command;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.datasource.DatabaseType;
import org.idp.server.basic.exception.UnSupportedException;

public class IdentityVerificationConfigCommandSqlExecutors {

  Map<DatabaseType, IdentityVerificationConfigCommandSqlExecutor> executors;

  public IdentityVerificationConfigCommandSqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(DatabaseType.POSTGRESQL, new PostgresqlExecutor());
  }

  public IdentityVerificationConfigCommandSqlExecutor get(DatabaseType databaseType) {
    IdentityVerificationConfigCommandSqlExecutor executor = executors.get(databaseType);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + databaseType.name());
    }

    return executor;
  }
}
