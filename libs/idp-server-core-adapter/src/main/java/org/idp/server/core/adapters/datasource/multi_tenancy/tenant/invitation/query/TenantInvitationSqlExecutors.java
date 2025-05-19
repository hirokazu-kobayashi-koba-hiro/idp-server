package org.idp.server.core.adapters.datasource.multi_tenancy.tenant.invitation.query;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.datasource.DatabaseType;
import org.idp.server.basic.exception.UnSupportedException;

public class TenantInvitationSqlExecutors {

  Map<DatabaseType, TenantInvitationSqlExecutor> executors;

  public TenantInvitationSqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(DatabaseType.POSTGRESQL, new PostgresqlExecutor());
    executors.put(DatabaseType.MYSQL, new MysqlExecutor());
  }

  public TenantInvitationSqlExecutor get(DatabaseType databaseType) {
    TenantInvitationSqlExecutor executor = executors.get(databaseType);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + databaseType.name());
    }

    return executor;
  }
}
