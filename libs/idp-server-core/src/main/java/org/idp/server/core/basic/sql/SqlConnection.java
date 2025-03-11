package org.idp.server.core.basic.sql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class SqlConnection {
  Connection connection;

  public SqlConnection(String url, String username, String password) {
    this.connection = createConnection(url, username, password);
  }

  Connection createConnection(String url, String username, String password) {
    try {
      Connection connection = DriverManager.getConnection(url, username, password);
      connection.setAutoCommit(true);
      return connection;
    } catch (SQLException exception) {
      throw new SqlRuntimeException(exception.getMessage(), exception);
    }
  }

  public Connection connection() {
    return connection;
  }

  public void commit() {
    try {
      connection.commit();
    } catch (SQLException exception) {
      throw new SqlRuntimeException(exception.getMessage(), exception);
    }
  }

  public void rollback() {
    try {
      connection.rollback();
    } catch (SQLException exception) {
      throw new SqlRuntimeException(exception.getMessage(), exception);
    }
  }
}
