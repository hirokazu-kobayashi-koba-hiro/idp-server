package org.idp.server.adapters.springboot.infrastructure.datasource.user;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Objects;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

public class CustomPropertiesTypeHandler extends BaseTypeHandler<HashMap<String, Object>> {

  ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public void setNonNullParameter(
      PreparedStatement ps, int i, HashMap<String, Object> parameter, JdbcType jdbcType)
      throws SQLException {
    // nothing
  }

  @Override
  public HashMap<String, Object> getNullableResult(ResultSet rs, String columnName)
      throws SQLException {
    String value = rs.getString(columnName);
    try {
      if (Objects.isNull(value) || value.isEmpty()) {
        return new HashMap<>();
      }
      return objectMapper.readValue(value, new TypeReference<>() {});
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public HashMap<String, Object> getNullableResult(ResultSet rs, int columnIndex)
      throws SQLException {
    String value = rs.getString(columnIndex);
    try {
      if (Objects.isNull(value) || value.isEmpty()) {
        return new HashMap<>();
      }
      return objectMapper.readValue(value, new TypeReference<>() {});
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public HashMap<String, Object> getNullableResult(CallableStatement cs, int columnIndex)
      throws SQLException {
    String value = cs.getString(columnIndex);
    try {
      if (Objects.isNull(value) || value.isEmpty()) {
        return new HashMap<>();
      }
      return objectMapper.readValue(value, new TypeReference<>() {});
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
