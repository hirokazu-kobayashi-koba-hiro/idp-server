/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.oidc.request;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.datasource.DatabaseType;
import org.idp.server.platform.exception.UnSupportedException;

public class AuthorizationRequestSqlExecutors {

  Map<DatabaseType, AuthorizationRequestSqlExecutor> executors;

  public AuthorizationRequestSqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(DatabaseType.POSTGRESQL, new PostgresqlSqlExecutor());
    executors.put(DatabaseType.MYSQL, new MysqlSqlExecutor());
  }

  public AuthorizationRequestSqlExecutor get(DatabaseType databaseType) {
    AuthorizationRequestSqlExecutor executor = executors.get(databaseType);

    if (executor == null) {
      throw new UnSupportedException("Unknown dialect " + databaseType.name());
    }

    return executor;
  }
}
