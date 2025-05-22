/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.config;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import org.idp.server.platform.datasource.*;
import org.idp.server.platform.log.LoggerWrapper;

public class HikariConnectionProvider implements DbConnectionProvider {

  Map<DatabaseType, HikariDataSource> writerConfigs;
  Map<DatabaseType, HikariDataSource> readerConfigs;
  LoggerWrapper log = LoggerWrapper.getLogger(HikariConnectionProvider.class);

  public HikariConnectionProvider(DatabaseConfig databaseConfig) {
    this.writerConfigs = HikariDataSourceFactory.create(databaseConfig.writerConfigs());
    this.readerConfigs = HikariDataSourceFactory.create(databaseConfig.readerConfigs());
  }

  public Connection getConnection(DatabaseType databaseType) {
    OperationType type = OperationContext.get();
    HikariDataSource hikariDataSource =
        (type == OperationType.READ)
            ? readerConfigs.get(databaseType)
            : writerConfigs.get(databaseType);
    log.info(
        "DB connection for "
            + databaseType
            + " url: "
            + hikariDataSource.getJdbcUrl()
            + " max connection pool: "
            + hikariDataSource.getMaximumPoolSize());
    try {
      Connection connection = hikariDataSource.getConnection();
      connection.setAutoCommit(false);
      return connection;
    } catch (SQLException e) {
      throw new SqlRuntimeException("Failed to get DB connection", e);
    }
  }
}
