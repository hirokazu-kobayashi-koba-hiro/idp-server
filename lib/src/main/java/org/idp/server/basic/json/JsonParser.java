package org.idp.server.basic.json;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

/** JsonParser */
public class JsonParser {

  ObjectMapper objectMapper;

  JsonParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public static JsonParser create() {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
    objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    return new JsonParser(objectMapper);
  }

  public static JsonParser createWithSnakeCaseStrategy() {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
    objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    return new JsonParser(objectMapper);
  }

  public <TYPE> TYPE read(String value, Class<TYPE> typeClass) {
    try {
      return objectMapper.readValue(value, typeClass);
    } catch (JsonProcessingException exception) {
      throw new JsonRuntimeException(exception);
    }
  }

  public String write(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new JsonRuntimeException(exception);
    }
  }
}
