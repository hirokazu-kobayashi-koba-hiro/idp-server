package org.idp.server.core.basic.json;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class JsonConverter {

  ObjectMapper objectMapper;

  JsonConverter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public static JsonConverter create() {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
    objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    return new JsonConverter(objectMapper);
  }

  public static JsonConverter createWithSnakeCaseStrategy() {
    JavaTimeModule javaTimeModule = new JavaTimeModule();
    javaTimeModule.addDeserializer(
        LocalDateTime.class,
        new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")));
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
    objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    objectMapper.registerModule(javaTimeModule);
    return new JsonConverter(objectMapper);
  }

  public <TYPE> TYPE read(Object jsonObject, Class<TYPE> type) {
    String json = write(jsonObject);
    return read(json, type);
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
