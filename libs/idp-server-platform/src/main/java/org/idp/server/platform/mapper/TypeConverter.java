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

package org.idp.server.platform.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.log.LoggerWrapper;

public class TypeConverter {

  private static final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
  private static final LoggerWrapper log = LoggerWrapper.getLogger(TypeConverter.class);

  public static Object convert(Object value, String type) {
    if (value == null) {
      return null;
    }

    if (type == null) {
      return value;
    }

    try {
      return switch (type) {
        case "string" -> value.toString();
        case "int" -> {
          if (value instanceof Number) {
            yield ((Number) value).intValue();
          }
          yield Integer.parseInt(value.toString());
        }
        case "boolean" -> {
          if (value instanceof Boolean) {
            yield value;
          }
          yield Boolean.parseBoolean(value.toString());
        }
        case "list<string>" -> {
          if (value instanceof List) {
            List<?> list = (List<?>) value;
            List<String> result = new ArrayList<>();
            for (Object o : list) {
              result.add(o.toString());
            }
            yield result;
          }
          yield jsonConverter.read(value.toString(), List.class);
        }
        case "list<object>" -> {
          if (value instanceof List) {
            yield value;
          }
          yield jsonConverter.read(value.toString(), List.class);
        }
        case "map", "object" -> {
          if (value instanceof Map) {
            yield value;
          }
          yield jsonConverter.read(value.toString(), Map.class);
        }
        default -> value;
      };
    } catch (Exception e) {

      log.warn(e.getMessage(), e);
      return null;
    }
  }
}
