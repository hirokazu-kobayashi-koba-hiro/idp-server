package org.idp.server.core.adapters.datasource.identity.verification.config.query;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.datasource.DatabaseType;
import org.idp.server.platform.exception.UnSupportedException;

public class IdentityVerificationConfigSqlExecutors {

  Map<DatabaseType, IdentityVerificationConfigSqlExecutor> executors;

  public IdentityVerificationConfigSqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(DatabaseType.POSTGRESQL, new PostgresqlExecutor());
  }

  public IdentityVerificationConfigSqlExecutor get(DatabaseType databaseType) {
    IdentityVerificationConfigSqlExecutor executor = executors.get(databaseType);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + databaseType.name());
    }

    return executor;
  }
}
