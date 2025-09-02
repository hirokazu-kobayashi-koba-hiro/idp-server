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

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

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
        if (param instanceof Long longValue) {
          prepareStatement.setLong(index, longValue);
        }
        if (param instanceof Boolean booleanValue) {
          prepareStatement.setBoolean(index, booleanValue);
        }
        if (param instanceof byte[] binary) {
          prepareStatement.setBytes(index, binary);
        }
        if (param instanceof UUID uuid) {
          prepareStatement.setObject(index, uuid);
        }
        if (param instanceof LocalDateTime localDateTime) {
          prepareStatement.setObject(index, localDateTime);
        }
        if (param == null) {
          prepareStatement.setObject(index, null);
        }
        index++;
      }

      List<Map<String, String>> results = select(sql, prepareStatement);
      if (results.isEmpty()) {
        return Map.of();
      }
      if (results.size() > 1) {
        throw new SqlTooManyResultsException(String.format("find results (%d)", results.size()));
      }
      return results.get(0);
    } catch (SQLException exception) {
      switch (SqlErrorClassifier.classify(exception)) {
        case UNIQUE_VIOLATION ->
            throw new SqlDuplicateKeyException("Duplicate key violation", exception);
        case NOT_NULL_VIOLATION, CHECK_VIOLATION ->
            throw new SqlBadRequestException("Invalid data for", exception);
        default -> throw new SqlRuntimeException("Sql execution is error", exception);
      }
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
        if (param instanceof UUID uuid) {
          prepareStatement.setObject(index, uuid);
        }
        if (param instanceof LocalDateTime localDateTime) {
          prepareStatement.setObject(index, localDateTime);
        }
        if (param == null) {
          prepareStatement.setObject(index, null);
        }
        index++;
      }

      List<Map<String, String>> results = select(sql, prepareStatement);
      if (results.isEmpty()) {
        return List.of();
      }
      return results;
    } catch (SQLException exception) {
      switch (SqlErrorClassifier.classify(exception)) {
        case UNIQUE_VIOLATION ->
            throw new SqlDuplicateKeyException("Duplicate key violation", exception);
        case NOT_NULL_VIOLATION, CHECK_VIOLATION ->
            throw new SqlBadRequestException("Invalid data for", exception);
        default -> throw new SqlRuntimeException("Sql execution is error", exception);
      }
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
        if (param instanceof UUID uuid) {
          prepareStatement.setObject(index, uuid);
        }
        if (param instanceof LocalDateTime localDateTime) {
          prepareStatement.setObject(index, localDateTime);
        }
        if (param == null) {
          prepareStatement.setObject(index, null);
        }
        index++;
      }

      List<Map<String, Object>> results = selectWithType(sql, prepareStatement);
      if (results.isEmpty()) {
        return List.of();
      }
      return results;
    } catch (SQLException exception) {
      switch (SqlErrorClassifier.classify(exception)) {
        case UNIQUE_VIOLATION ->
            throw new SqlDuplicateKeyException("Duplicate key violation", exception);
        case NOT_NULL_VIOLATION, CHECK_VIOLATION ->
            throw new SqlBadRequestException("Invalid data for", exception);
        default -> throw new SqlRuntimeException("Sql execution is error", exception);
      }
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
        if (param instanceof UUID uuid) {
          prepareStatement.setObject(index, uuid);
        }
        if (param instanceof LocalDateTime localDateTime) {
          prepareStatement.setObject(index, localDateTime);
        }
        if (param == null) {
          prepareStatement.setObject(index, null);
        }
        index++;
      }
      prepareStatement.executeUpdate();

    } catch (SQLException exception) {
      switch (SqlErrorClassifier.classify(exception)) {
        case UNIQUE_VIOLATION ->
            throw new SqlDuplicateKeyException("Duplicate key violation", exception);
        case NOT_NULL_VIOLATION, CHECK_VIOLATION ->
            throw new SqlBadRequestException("Invalid data for", exception);
        default -> throw new SqlRuntimeException("Sql execution is error", exception);
      }
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
