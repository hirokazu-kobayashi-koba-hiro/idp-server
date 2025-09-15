/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.core.adapters.datasource.config;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import org.idp.server.platform.datasource.*;
import org.idp.server.platform.log.LoggerWrapper;

public class HikariConnectionProvider implements DbConnectionProvider {

  HikariDatabaseConfig adminDatabaseConfig;
  HikariDatabaseConfig appDatabaseConfig;
  LoggerWrapper log = LoggerWrapper.getLogger(HikariConnectionProvider.class);

  public HikariConnectionProvider(
      DatabaseConfig adminDatabaseConfig, DatabaseConfig appDatabaseConfig) {
    this.adminDatabaseConfig =
        new HikariDatabaseConfig(
            HikariDataSourceFactory.create(adminDatabaseConfig.writerConfigs()),
            HikariDataSourceFactory.create(adminDatabaseConfig.readerConfigs()));
    this.appDatabaseConfig =
        new HikariDatabaseConfig(
            HikariDataSourceFactory.create(appDatabaseConfig.writerConfigs()),
            HikariDataSourceFactory.create(appDatabaseConfig.readerConfigs()));
  }

  public Connection getConnection(DatabaseType databaseType, boolean admin) {
    OperationType type = OperationContext.get();

    if (admin) {
      HikariDataSource hikariDataSource =
          (type == OperationType.READ)
              ? adminDatabaseConfig.readerConfigs().get(databaseType)
              : adminDatabaseConfig.writerConfigs().get(databaseType);
      try {

        log.debug(
            "DB connection for "
                + databaseType
                + " url: "
                + hikariDataSource.getJdbcUrl()
                + " max connection pool: "
                + hikariDataSource.getMaximumPoolSize());

        Connection connection = hikariDataSource.getConnection();
        connection.setAutoCommit(false);
        return connection;
      } catch (SQLException e) {
        throw new SqlRuntimeException("Failed to get DB connection", e);
      }
    }

    HikariDataSource hikariDataSource =
        (type == OperationType.READ)
            ? appDatabaseConfig.readerConfigs().get(databaseType)
            : appDatabaseConfig.writerConfigs().get(databaseType);
    try {

      log.debug(
          "DB connection for "
              + databaseType
              + " url: "
              + hikariDataSource.getJdbcUrl()
              + " max connection pool: "
              + hikariDataSource.getMaximumPoolSize());

      Connection connection = hikariDataSource.getConnection();
      connection.setAutoCommit(false);
      return connection;
    } catch (SQLException e) {
      throw new SqlRuntimeException("Failed to get DB connection", e);
    }
  }
}
