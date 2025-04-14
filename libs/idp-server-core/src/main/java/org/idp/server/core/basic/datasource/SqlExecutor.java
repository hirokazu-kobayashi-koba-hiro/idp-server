package org.idp.server.core.basic.datasource;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SqlExecutor {
  private final Connection connection;

  public SqlExecutor() {
    this.connection = TransactionManager.getConnection();
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
        if (param instanceof Long longValue) {
          prepareStatement.setLong(index, longValue);
        }
        if (param instanceof Boolean booleanValue) {
          prepareStatement.setBoolean(index, booleanValue);
        }
        if (param instanceof byte[] binary) {
          prepareStatement.setBytes(index, binary);
        }
        if (param == null) {
          prepareStatement.setString(index, "");
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

  public List<Map<String, Object>> selectListWithType(String sql, List<Object> params) {
    try (PreparedStatement prepareStatement = connection.prepareStatement(sql)) {

      int index = 1;
      for (Object param : params) {
        if (param instanceof String stringValue) {
          prepareStatement.setString(index, stringValue);
        }
        if (param instanceof Integer integerValue) {
          prepareStatement.setInt(index, integerValue);
        }
        if (param instanceof Long longValue) {
          prepareStatement.setLong(index, longValue);
        }
        if (param instanceof Boolean booleanValue) {
          prepareStatement.setBoolean(index, booleanValue);
        }
        if (param instanceof byte[] binary) {
          prepareStatement.setBytes(index, binary);
        }
        if (param == null) {
          prepareStatement.setString(index, "");
        }
        index++;
      }

      List<Map<String, Object>> results = selectWithType(sql, prepareStatement);
      if (results.isEmpty()) {
        return List.of();
      }
      return results;
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
        if (param instanceof Long longValue) {
          prepareStatement.setLong(index, longValue);
        }
        if (param instanceof Boolean booleanValue) {
          prepareStatement.setBoolean(index, booleanValue);
        }
        if (param instanceof byte[] binary) {
          prepareStatement.setBytes(index, binary);
        }
        if (param == null) {
          prepareStatement.setString(index, null);
        }
        index++;
      }
      prepareStatement.executeUpdate();

    } catch (SQLException exception) {
      throw new SqlRuntimeException(exception.getMessage(), exception);
    }
  }

  private List<Map<String, String>> select(String sql, PreparedStatement preparedStatement)
      throws SQLException {
    ResultSet resultSet = preparedStatement.executeQuery();
    ResultSetMetaData metaData = resultSet.getMetaData();
    int columnCount = metaData.getColumnCount();

    List<Map<String, String>> results = new ArrayList<>();
    while (resultSet.next()) {
      Map<String, String> row = new HashMap<>();
      for (int i = 1; i <= columnCount; i++) {
        String columnName = metaData.getColumnLabel(i);
        String value = resultSet.getString(columnName);
        row.put(columnName, value);
      }
      results.add(row);
    }
    return results;
  }

  private List<Map<String, Object>> selectWithType(String sql, PreparedStatement preparedStatement)
      throws SQLException {
    ResultSet resultSet = preparedStatement.executeQuery();
    ResultSetMetaData metaData = resultSet.getMetaData();
    int columnCount = metaData.getColumnCount();

    List<Map<String, Object>> results = new ArrayList<>();

    while (resultSet.next()) {
      Map<String, Object> row = new HashMap<>();
      for (int i = 1; i <= columnCount; i++) {
        String columnName = metaData.getColumnLabel(i);
        Object value = getTypedValue(resultSet, i, metaData.getColumnType(i));
        row.put(columnName, value);
      }
      results.add(row);
    }
    return results;
  }

  private Object getTypedValue(ResultSet resultSet, int index, int sqlType) throws SQLException {
    return switch (sqlType) {
      case Types.INTEGER -> resultSet.getInt(index);
      case Types.BIGINT -> resultSet.getLong(index);
      case Types.FLOAT -> resultSet.getFloat(index);
      case Types.DOUBLE -> resultSet.getDouble(index);
      case Types.DECIMAL, Types.NUMERIC -> resultSet.getBigDecimal(index);
      case Types.BOOLEAN -> resultSet.getBoolean(index);
      case Types.DATE -> resultSet.getDate(index);
      case Types.TIMESTAMP -> resultSet.getTimestamp(index).toLocalDateTime();
      case Types.TIME -> resultSet.getTime(index);
      case Types.VARCHAR, Types.CHAR, Types.LONGVARCHAR -> resultSet.getString(index);
      case Types.BLOB -> resultSet.getBytes(index);
      case Types.NULL -> null;
      default -> resultSet.getObject(index);
    };
  }
}
