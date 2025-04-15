package org.idp.server.core.basic.datasource;

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
