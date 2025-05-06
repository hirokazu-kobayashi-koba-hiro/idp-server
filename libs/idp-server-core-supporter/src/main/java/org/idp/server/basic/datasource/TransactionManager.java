package org.idp.server.basic.datasource;

import java.sql.Connection;
import java.sql.SQLException;

public class TransactionManager {
  private static final ThreadLocal<Connection> connectionHolder = new ThreadLocal<>();
  private static DbConnectionProvider dbConnectionProvider;

  public static void configure(DbConnectionProvider provider) {
    dbConnectionProvider = provider;
  }

  public static void createConnection(DatabaseType databaseType) {
    if (connectionHolder.get() != null) {
      throw new SqlRuntimeException("Transaction already started");
    }
    OperationContext.set(OperationType.READ);
    Connection conn = dbConnectionProvider.getConnection(databaseType);
    connectionHolder.set(conn);
  }

  public static void beginTransaction(DatabaseType databaseType) {
    if (connectionHolder.get() != null) {
      throw new SqlRuntimeException("Transaction already started");
    }
    OperationContext.set(OperationType.WRITE);
    Connection conn = dbConnectionProvider.getConnection(databaseType);
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
    if (conn == null)
      return;
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
    if (conn == null)
      return;
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
}
