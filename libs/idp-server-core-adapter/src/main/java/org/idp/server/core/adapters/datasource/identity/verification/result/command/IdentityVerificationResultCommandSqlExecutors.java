package org.idp.server.core.adapters.datasource.identity.verification.result.command;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.basic.datasource.DatabaseType;
import org.idp.server.core.type.exception.UnSupportedException;

public class IdentityVerificationResultCommandSqlExecutors {

  Map<DatabaseType, IdentityVerificationResultCommandSqlExecutor> executors;

  public IdentityVerificationResultCommandSqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(DatabaseType.POSTGRESQL, new PostgresqlExecutor());
  }

  public IdentityVerificationResultCommandSqlExecutor get(DatabaseType databaseType) {
    IdentityVerificationResultCommandSqlExecutor executor = executors.get(databaseType);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + databaseType.name());
    }

    return executor;
  }
}
