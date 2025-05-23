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

package org.idp.server.basic.json;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class JsonConverter {

  private static final JsonConverter DEFAULT = create();
  private static final JsonConverter SNAKE_CASE = createWithSnakeCaseStrategy();

  public static JsonConverter defaultInstance() {
    return DEFAULT;
  }

  public static JsonConverter snakeCaseInstance() {
    return SNAKE_CASE;
  }

  private final ObjectMapper objectMapper;

  private JsonConverter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  private static JsonConverter create() {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
    objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    return new JsonConverter(objectMapper);
  }

  private static JsonConverter createWithSnakeCaseStrategy() {
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

  public JsonNodeWrapper readTree(String jsonString) {
    try {

      return new JsonNodeWrapper(objectMapper.readTree(jsonString));
    } catch (JsonProcessingException exception) {
      throw new JsonRuntimeException(exception);
    }
  }

  public JsonNodeWrapper readTree(Map<String, Object> jsonObject) {
    try {

      String json = write(jsonObject);
      return new JsonNodeWrapper(objectMapper.readTree(json));
    } catch (JsonProcessingException exception) {
      throw new JsonRuntimeException(exception);
    }
  }

  public JsonNodeWrapper readTree(Object jsonObject) {
    try {

      String json = write(jsonObject);
      return new JsonNodeWrapper(objectMapper.readTree(json));
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
