package org.idp.sample.user;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

public class CredentialsTypeHandler extends BaseTypeHandler<List<Map<String, Object>>> {

  ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public void setNonNullParameter(
      PreparedStatement ps, int i, List<Map<String, Object>> parameter, JdbcType jdbcType)
      throws SQLException {}

  @Override
  public List<Map<String, Object>> getNullableResult(ResultSet rs, String columnName)
      throws SQLException {
    String value = rs.getString(columnName);
    try {
      if (Objects.isNull(value) || value.isEmpty()) {
        return List.of();
      }
      return objectMapper.readValue(value, new TypeReference<>() {});
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public List<Map<String, Object>> getNullableResult(ResultSet rs, int columnIndex)
      throws SQLException {
    String value = rs.getString(columnIndex);
    try {
      if (Objects.isNull(value) || value.isEmpty()) {
        return List.of();
      }
      return objectMapper.readValue(value, new TypeReference<>() {});
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public List<Map<String, Object>> getNullableResult(CallableStatement cs, int columnIndex)
      throws SQLException {
    String value = cs.getString(columnIndex);
    try {
      if (Objects.isNull(value) || value.isEmpty()) {
        return List.of();
      }
      return objectMapper.readValue(value, new TypeReference<>() {});
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
