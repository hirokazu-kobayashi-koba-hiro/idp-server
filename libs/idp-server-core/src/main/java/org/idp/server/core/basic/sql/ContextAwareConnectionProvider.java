package org.idp.server.core.basic.sql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;

public class ContextAwareConnectionProvider implements ConnectionProvider {
  Map<DatabaseType, DbCredentials> writerConfigs;
  Map<DatabaseType, DbCredentials> readerConfigs;

  public ContextAwareConnectionProvider(DatabaseConfig databaseConfig) {
    this.writerConfigs = databaseConfig.writerConfigs();
    this.readerConfigs = databaseConfig.readerConfigs();
  }

  public Connection getConnection(DatabaseType databaseType) {
    OperationType type = OperationContext.get();
    DbCredentials credentials =
        (type == OperationType.READ)
            ? readerConfigs.get(databaseType)
            : writerConfigs.get(databaseType);
    try {
      Connection conn =
          DriverManager.getConnection(
              credentials.url(), credentials.username(), credentials.password());
      conn.setAutoCommit(false);
      return conn;
    } catch (SQLException e) {
      throw new SqlRuntimeException("Failed to get DB connection", e);
    }
  }
}
