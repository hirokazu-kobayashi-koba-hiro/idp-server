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

package org.idp.server.platform.http;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;

public class HttpRequestDynamicHeaderMapper {

  Map<String, String> headers;
  HttpRequestHeaderMappingRules mappingRules;
  JsonPathWrapper bodyJsonPath;

  public HttpRequestDynamicHeaderMapper(
      Map<String, String> headers,
      JsonNodeWrapper body,
      HttpRequestHeaderMappingRules mappingRules) {
    this.headers = headers;
    this.mappingRules = mappingRules;
    this.bodyJsonPath = new JsonPathWrapper(body.toJson());
  }

  public Map<String, String> toHeaders() {
    Map<String, String> resolvedHeaders = new HashMap<>();

    for (HttpRequestMappingRule rule : mappingRules) {
      Object value = null;

      switch (rule.getSource()) {
        case "header" -> value = extractFromHeader(rule);
        case "body" -> value = extractFromBody(rule);
      }

      if (value == null) {
        continue;
      }

      resolvedHeaders.put(rule.getTo(), value.toString());
    }

    return resolvedHeaders;
  }

  private Object extractFromHeader(HttpRequestMappingRule rule) {
    String from = rule.getFrom().replace("$.", "");
    String rawValue = headers.get(from);
    if (rawValue == null) return null;

    return switch (rule.getType()) {
      case "string" -> rawValue;
      case "boolean" -> Boolean.parseBoolean(rawValue);
      case "int" -> {
        try {
          yield Integer.parseInt(rawValue);
        } catch (NumberFormatException e) {
          yield null;
        }
      }
      default -> null;
    };
  }

  private Object extractFromBody(HttpRequestMappingRule rule) {

    if (rule.hasItemIndex()) {
      int index = rule.getItemIndexOrDefault(0);

      return switch (rule.getType()) {
        case "list<string>" -> {
          List<String> strings = bodyJsonPath.readAsStringList(rule.getFrom());
          if (strings.size() > index) {
            yield strings.get(index);
          }
          yield null;
        }
        case "list<object>" -> {
          List<Map<String, Object>> mapList = bodyJsonPath.readAsMapList(rule.getFrom());
          String field = rule.getFieldOrDefault("");
          if (mapList.size() > index) {
            Map<String, Object> objectMap = mapList.get(index);
            yield objectMap.get(field);
          }
          yield null;
        }
        default -> null;
      };
    }

    return switch (rule.getType()) {
      case "string" -> bodyJsonPath.readAsString(rule.getFrom());
      case "boolean" -> bodyJsonPath.readAsBoolean(rule.getFrom());
      case "int" -> bodyJsonPath.readAsInt(rule.getFrom());
      default -> null;
    };
  }
}
