/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.idp.server.platform.datasource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;

public class DefaultDbDbConnectionProvider implements DbConnectionProvider {
  Map<DatabaseType, DbConfig> writerConfigs;
  Map<DatabaseType, DbConfig> readerConfigs;

  public DefaultDbDbConnectionProvider(DatabaseConfig databaseConfig) {
    this.writerConfigs = databaseConfig.writerConfigs();
    this.readerConfigs = databaseConfig.readerConfigs();
  }

  public Connection getConnection(DatabaseType databaseType) {
    OperationType type = OperationContext.get();
    DbConfig dbConfig =
        (type == OperationType.READ)
            ? readerConfigs.get(databaseType)
            : writerConfigs.get(databaseType);
    try {
      Connection connection =
          DriverManager.getConnection(dbConfig.url(), dbConfig.username(), dbConfig.password());
      connection.setAutoCommit(false);
      return connection;
    } catch (SQLException e) {
      throw new SqlRuntimeException("Failed to get DB connection", e);
    }
  }
}
