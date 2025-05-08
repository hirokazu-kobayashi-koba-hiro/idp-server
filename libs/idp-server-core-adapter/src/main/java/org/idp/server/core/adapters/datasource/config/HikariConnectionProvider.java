package org.idp.server.core.adapters.datasource.config;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import org.idp.server.basic.datasource.*;
import org.idp.server.basic.log.LoggerWrapper;

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
