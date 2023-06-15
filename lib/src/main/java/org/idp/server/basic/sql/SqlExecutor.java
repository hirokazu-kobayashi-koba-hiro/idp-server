package org.idp.server.basic.sql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SqlExecutor {
  Connection connection;

  public SqlExecutor(Connection connection) {
    this.connection = connection;
  }

  public Map<String, String> selectOne(String sql) {
    try (Statement statement = connection.createStatement()) {
      List<Map<String, String>> results = select(sql, statement);
      if (results.isEmpty()) {
        return Map.of();
      }
      if (results.size() > 1) {
        throw new RuntimeException(String.format("find results (%d)", results.size()));
      }
      return results.get(0);
    } catch (SQLException exception) {
      throw new SqlRuntimeException(exception.getMessage(), exception);
    }
  }

  public List<Map<String, String>> selectList(String sql) {
    try (Statement statement = connection.createStatement()) {
      List<Map<String, String>> results = select(sql, statement);
      if (results.isEmpty()) {
        return List.of();
      }
      return results;
    } catch (SQLException exception) {
      throw new SqlRuntimeException(exception.getMessage(), exception);
    }
  }

  public void execute(String sql) {
    try (Statement statement = connection.createStatement()) {
      statement.execute(sql);
    } catch (SQLException exception) {
      throw new SqlRuntimeException(exception.getMessage(), exception);
    }
  }

  private List<Map<String, String>> select(String sql, Statement statement) throws SQLException {
    ResultSet resultSet = statement.executeQuery(sql);
    List<String> columnNames = SqlAnalyzer.columnNames(sql);
    List<Map<String, String>> results = new ArrayList<>();
    while (resultSet.next()) {
      Map<String, String> map = new HashMap<>();
      for (String columnName : columnNames) {
        String value = resultSet.getString(columnName);
        map.put(columnName, value);
      }
      results.add(map);
    }
    return results;
  }
}
