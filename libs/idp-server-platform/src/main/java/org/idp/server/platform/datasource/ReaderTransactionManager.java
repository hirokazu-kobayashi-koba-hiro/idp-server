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


package org.idp.server.platform.datasource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ReaderTransactionManager {
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
