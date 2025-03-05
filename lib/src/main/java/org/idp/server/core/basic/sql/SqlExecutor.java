package org.idp.server.core.basic.sql;

import java.sql.*;
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

  public Map<String, String> selectOne(String sql, List<Object> params) {
    try (PreparedStatement prepareStatement = connection.prepareStatement(sql)) {

      int index = 1;
      for (Object param : params) {
        if (param instanceof String stringValue) {
          prepareStatement.setString(index, stringValue);
        }
        if (param instanceof Integer integerValue) {
          prepareStatement.setInt(index, integerValue);
        }
        index++;
      }

      List<Map<String, String>> results = select(sql, prepareStatement);
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

  public List<Map<String, String>> selectList(String sql, List<Object> params) {
    try (PreparedStatement prepareStatement = connection.prepareStatement(sql)) {

      int index = 1;
      for (Object param : params) {
        if (param instanceof String stringValue) {
          prepareStatement.setString(index, stringValue);
        }
        if (param instanceof Integer integerValue) {
          prepareStatement.setInt(index, integerValue);
        }
        if (param instanceof Object objectValue) {
          prepareStatement.setObject(index, objectValue);
        }
        index++;
      }

      List<Map<String, String>> results = select(sql, prepareStatement);
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

  public void execute(String sql, List<Object> params) {
    try (PreparedStatement prepareStatement = connection.prepareStatement(sql)) {

      int index = 1;
      for (Object param : params) {

        if (param instanceof String stringValue) {
          prepareStatement.setString(index, stringValue);
        }
        if (param instanceof Integer integerValue) {
          prepareStatement.setInt(index, integerValue);
        }
        if (param instanceof Object objectValue) {
          prepareStatement.setObject(index, objectValue);
        }
        index++;
      }
      prepareStatement.executeUpdate();

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

  private List<Map<String, String>> select(String sql, PreparedStatement preparedStatement)
      throws SQLException {
    ResultSet resultSet = preparedStatement.executeQuery();
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
