package org.idp.server.platform.datasource;

import java.sql.Connection;
import java.sql.SQLException;
import org.idp.server.platform.log.LoggerWrapper;

public class TransactionManager {
  private static final LoggerWrapper log = LoggerWrapper.getLogger(TransactionManager.class);
  private static final ThreadLocal<Connection> connectionHolder = new ThreadLocal<>();
  private static DbConnectionProvider dbConnectionProvider;

  public static void configure(DbConnectionProvider provider) {
    dbConnectionProvider = provider;
  }

  public static void createConnection(DatabaseType databaseType, String tenantIdentifier) {
    if (connectionHolder.get() != null) {
      throw new SqlRuntimeException("Transaction already started");
    }
    OperationContext.set(OperationType.READ);
    Connection conn = dbConnectionProvider.getConnection(databaseType);
    if (databaseType == DatabaseType.POSTGRESQL) {
      setTenantId(conn, tenantIdentifier);
    }
    connectionHolder.set(conn);
  }

  public static void beginTransaction(DatabaseType databaseType, String tenantIdentifier) {
    if (connectionHolder.get() != null) {
      throw new SqlRuntimeException("Transaction already started");
    }
    OperationContext.set(OperationType.WRITE);
    Connection conn = dbConnectionProvider.getConnection(databaseType);
    if (databaseType == DatabaseType.POSTGRESQL) {
      setTenantId(conn, tenantIdentifier);
    }
    connectionHolder.set(conn);
  }

  public static Connection getConnection() {
    Connection conn = connectionHolder.get();
    if (conn == null) {
      throw new SqlRuntimeException("No active transaction");
    }
    return conn;
  }

  public static void commitTransaction() {
    Connection conn = connectionHolder.get();
    if (conn == null) return;
    try {
      conn.commit();
    } catch (SQLException e) {
      throw new SqlRuntimeException("Failed to commit transaction", e);
    } finally {
      closeConnection();
    }
  }

  public static void rollbackTransaction() {
    Connection conn = connectionHolder.get();
    if (conn == null) return;
    try {
      conn.rollback();
    } catch (SQLException e) {
      throw new SqlRuntimeException("Failed to rollback transaction", e);
    } finally {
      closeConnection();
    }
  }

  public static void closeConnection() {
    Connection conn = connectionHolder.get();
    if (conn != null) {
      try {
        conn.close();
      } catch (SQLException e) {
        throw new SqlRuntimeException("Failed to close connection", e);
      } finally {
        connectionHolder.remove();
        OperationContext.clear();
      }
    }
  }

  private static void setTenantId(Connection conn, String tenantIdentifier) {
    log.info("[RLS] SET app.tenant_id = '" + tenantIdentifier + "'");

    try (var stmt = conn.createStatement()) {
      stmt.execute("SET app.tenant_id = '" + tenantIdentifier + "'");
    } catch (SQLException e) {
      throw new SqlRuntimeException("Failed to set tenant_id", e);
    }
  }
}
