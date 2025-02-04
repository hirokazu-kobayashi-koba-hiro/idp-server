package org.idp.server.basic.sql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class TransactionManager {
  private static final ThreadLocal<Connection> connectionHolder = new ThreadLocal<>();
  private static String dbUrl;
  private static String dbUsername;
  private static String dbPassword;

  public static void setConnectionConfig(String url, String username, String password) {
    dbUrl = url;
    dbUsername = username;
    dbPassword = password;
  }

  private static Connection createConnection() {
    if (dbUrl == null || dbUsername == null || dbPassword == null) {
      throw new SqlRuntimeException(
          "Database connection is not configured. Call setConnectionConfig() first.");
    }
    try {
      Connection connection = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
      connection.setAutoCommit(false);
      return connection;
    } catch (SQLException exception) {
      throw new SqlRuntimeException("Failed to create connection", exception);
    }
  }

  public static void beginTransaction() {
    if (connectionHolder.get() != null) {
      throw new SqlRuntimeException("Transaction already started in this thread.");
    }
    connectionHolder.set(createConnection());
  }

  public static void commitTransaction() {
    Connection connection = connectionHolder.get();
    if (connection == null) {
      throw new SqlRuntimeException("No active transaction to commit.");
    }
    try {
      connection.commit();
    } catch (SQLException exception) {
      throw new SqlRuntimeException("Failed to commit transaction", exception);
    } finally {
      closeConnection();
    }
  }

  public static void rollbackTransaction() {
    Connection connection = connectionHolder.get();
    if (connection == null) {
      throw new SqlRuntimeException("No active transaction to rollback.");
    }
    try {
      connection.rollback();
    } catch (SQLException exception) {
      throw new SqlRuntimeException("Failed to rollback transaction", exception);
    } finally {
      closeConnection();
    }
  }

  private static void closeConnection() {
    Connection connection = connectionHolder.get();
    if (connection != null) {
      try {
        connection.close();
      } catch (SQLException exception) {
        throw new SqlRuntimeException("Failed to close connection", exception);
      } finally {
        connectionHolder.remove();
      }
    }
  }

  public static Connection getConnection() {
    Connection connection = connectionHolder.get();
    if (connection == null) {
      throw new SqlRuntimeException("No active transaction. Call beginTransaction() first.");
    }
    return connection;
  }
}
