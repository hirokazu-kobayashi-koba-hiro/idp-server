package org.idp.server.core.adapters.datasource.identity.verification.application.command;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.datasource.DatabaseType;
import org.idp.server.basic.exception.UnSupportedException;

public class IdentityVerificationApplicationCommandSqlExecutors {

  Map<DatabaseType, IdentityVerificationApplicationCommandSqlExecutor> executors;

  public IdentityVerificationApplicationCommandSqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(DatabaseType.POSTGRESQL, new PostgresqlExecutor());
  }

  public IdentityVerificationApplicationCommandSqlExecutor get(DatabaseType databaseType) {
    IdentityVerificationApplicationCommandSqlExecutor executor = executors.get(databaseType);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + databaseType.name());
    }

    return executor;
  }
}
