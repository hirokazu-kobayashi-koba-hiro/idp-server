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

package org.idp.server.platform.json;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

public class JsonConverter {

  private static final JsonConverter DEFAULT = create();
  private static final JsonConverter SNAKE_CASE = createWithSnakeCaseStrategy();

  public static JsonConverter defaultInstance() {
    return DEFAULT;
  }

  public static JsonConverter snakeCaseInstance() {
    return SNAKE_CASE;
  }

  private JsonMapper jsonMapper;

  private JsonConverter(JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  /**
   * Creates a default JsonConverter instance.
   *
   * <p>Collection coercion is configured to handle backward compatibility: the client configuration
   * "contacts" field was changed from String to String[] on 2025-09-17. When a plain string value
   * is encountered where a collection is expected, it is coerced to null instead of throwing an
   * error.
   */
  private static JsonConverter create() {
    JsonMapper jsonMapper =
        JsonMapper.builder()
            .changeDefaultVisibility(
                vc ->
                    vc.withVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
                        .withVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY))
            .withCoercionConfig(
                LogicalType.Collection,
                config -> {
                  config.setCoercion(CoercionInputShape.String, CoercionAction.AsNull);
                  config.setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsNull);
                })
            .build();
    return new JsonConverter(jsonMapper);
  }

  /** Creates a snake_case JsonConverter instance. See {@link #create()} for coercion details. */
  private static JsonConverter createWithSnakeCaseStrategy() {
    JsonMapper jsonMapper =
        JsonMapper.builder()
            .changeDefaultVisibility(
                vc ->
                    vc.withVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
                        .withVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY))
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .withCoercionConfig(
                LogicalType.Collection,
                config -> {
                  config.setCoercion(CoercionInputShape.String, CoercionAction.AsNull);
                  config.setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsNull);
                })
            .build();
    return new JsonConverter(jsonMapper);
  }

  public <TYPE> TYPE read(Object jsonObject, Class<TYPE> type) {
    return jsonMapper.convertValue(jsonObject, type);
  }

  public <TYPE> TYPE read(String value, Class<TYPE> typeClass) {
    try {
      return jsonMapper.readValue(value, typeClass);
    } catch (JacksonException exception) {
      throw new JsonRuntimeException(exception);
    }
  }

  public JsonNodeWrapper readTree(String jsonString) {
    try {
      return new JsonNodeWrapper(jsonMapper.readTree(jsonString));
    } catch (JacksonException exception) {
      throw new JsonRuntimeException(exception);
    }
  }

  public JsonNodeWrapper readTree(Map<String, Object> jsonObject) {
    return new JsonNodeWrapper(jsonMapper.valueToTree(jsonObject));
  }

  public JsonNodeWrapper readTree(Object jsonObject) {
    return new JsonNodeWrapper(jsonMapper.valueToTree(jsonObject));
  }

  public String write(Object value) {
    try {
      return jsonMapper.writeValueAsString(value);
    } catch (JacksonException exception) {
      throw new JsonRuntimeException(exception);
    }
  }
}
