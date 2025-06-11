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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.*;

/**
 * A lightweight wrapper for Jackson's {@link JsonNode} providing convenient and safe access
 * methods. Useful for parsing dynamic JSON (e.g., JSONB in PostgreSQL) with type handling and null
 * safety.
 */
public class JsonNodeWrapper {
  JsonNode jsonNode;

  /**
   * Returns an empty JSON object wrapper.
   *
   * @return a wrapper around an empty object node
   */
  public static JsonNodeWrapper empty() {
    return new JsonNodeWrapper(JsonNodeFactory.instance.objectNode());
  }

  public JsonNodeWrapper(JsonNode jsonNode) {
    this.jsonNode = jsonNode;
  }

  public static JsonNodeWrapper fromMap(Map<String, Object> map) {
    if (map == null || map.isEmpty()) {
      return empty();
    }
    JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
    return jsonConverter.readTree(map);
  }

  public static JsonNodeWrapper fromObject(Object object) {
    if (object == null) {
      return empty();
    }
    JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
    return jsonConverter.readTree(object);
  }

  /**
   * Creates a wrapper from a string-form JSON.
   *
   * @param json JSON string
   * @return a {@link JsonNodeWrapper} instance
   */
  public static JsonNodeWrapper fromString(String json) {
    if (json == null || json.isEmpty()) {
      return empty();
    }
    JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
    return jsonConverter.readTree(json);
  }

  public Iterator<String> fieldNames() {
    return jsonNode.fieldNames();
  }

  public List<String> fieldNamesAsList() {
    List<String> names = new ArrayList<>();
    fieldNames().forEachRemaining(names::add);
    return names;
  }

  public List<String> toList() {
    List<String> list = new ArrayList<>();
    if (jsonNode.isArray()) {
      Iterator<JsonNode> iterator = jsonNode.elements();
      while (iterator.hasNext()) {
        list.add(iterator.next().asText());
      }
    }
    return list;
  }

  public JsonNodeWrapper getValueAsJsonNode(String fieldName) {
    return new JsonNodeWrapper(jsonNode.get(fieldName));
  }

  /**
   * Converts a field (expected to be a JSON array) to a list of {@link JsonNodeWrapper}.
   *
   * @param fieldName name of the array field
   * @return list of wrapped elements
   */
  public List<JsonNodeWrapper> getValueAsJsonNodeList(String fieldName) {
    List<JsonNodeWrapper> values = new ArrayList<>();
    Iterator<JsonNode> iterator = jsonNode.get(fieldName).elements();
    while (iterator.hasNext()) {
      values.add(new JsonNodeWrapper(iterator.next()));
    }
    return values;
  }

  public List<Map<String, Object>> toListAsMap() {
    List<Map<String, Object>> values = new ArrayList<>();
    Iterator<JsonNode> iterator = jsonNode.elements();
    while (iterator.hasNext()) {
      values.add(new JsonNodeWrapper(iterator.next()).toMap());
    }
    return values;
  }

  public List<Map<String, Object>> getValueAsJsonNodeListAsMap(String fieldName) {
    List<Map<String, Object>> values = new ArrayList<>();
    Iterator<JsonNode> iterator = jsonNode.get(fieldName).elements();
    while (iterator.hasNext()) {
      values.add(new JsonNodeWrapper(iterator.next()).toMap());
    }
    return values;
  }

  public List<JsonNodeWrapper> elements() {
    List<JsonNodeWrapper> list = new ArrayList<>();
    if (jsonNode.isArray()) {
      Iterator<JsonNode> iterator = jsonNode.elements();
      while (iterator.hasNext()) {
        list.add(new JsonNodeWrapper(iterator.next()));
      }
    }
    return list;
  }

  public boolean contains(String fieldName) {
    return jsonNode.has(fieldName);
  }

  /**
   * Returns the raw node value of this wrapper.
   *
   * @return the underlying {@link JsonNode}
   */
  public Object node() {
    return jsonNode;
  }

  public Object getValue(String fieldName) {
    return jsonNode.get(fieldName);
  }

  /**
   * Retrieves the value for a field as a string. Returns an empty string if the field is absent.
   *
   * @param fieldName name of the JSON field
   * @return string value or empty string
   */
  public String getValueOrEmptyAsString(String fieldName) {
    if (!contains(fieldName)) {
      return "";
    }
    return jsonNode.get(fieldName).asText("");
  }

  public int getValueAsInt(String fieldName) {
    return jsonNode.get(fieldName).asInt();
  }

  public boolean getValueAsBoolean(String fieldName) {
    return jsonNode.get(fieldName).asBoolean();
  }

  public boolean optValueAsBoolean(String fieldName, boolean defaultValue) {
    return jsonNode.has(fieldName) ? jsonNode.get(fieldName).asBoolean() : defaultValue;
  }

  public String asText() {
    return jsonNode.asText();
  }

  /**
   * Converts the entire JSON object into a Java {@link Map}, recursively extracting primitive
   * values.
   *
   * @return map representing the JSON object
   */
  public Map<String, Object> toMap() {
    Map<String, Object> results = new HashMap<>();
    jsonNode
        .fieldNames()
        .forEachRemaining(
            fieldName -> {
              JsonNodeWrapper valueWrapper = getValueAsJsonNode(fieldName);
              JsonNode valueNode = (JsonNode) valueWrapper.node();
              Object value = toPrimitive(valueNode);
              results.put(fieldName, value);
            });
    return results;
  }

  public boolean exists() {
    return jsonNode != null;
  }

  public JsonNodeType nodeType() {
    if (jsonNode.isObject()) {
      return JsonNodeType.OBJECT;
    }
    if (jsonNode.isArray()) {
      return JsonNodeType.ARRAY;
    }
    if (jsonNode.isTextual()) {
      return JsonNodeType.STRING;
    }
    if (jsonNode.isBoolean()) {
      return JsonNodeType.BOOLEAN;
    }
    if (jsonNode.isInt()) {
      return JsonNodeType.INT;
    }
    if (jsonNode.isLong()) {
      return JsonNodeType.LONG;
    }
    if (jsonNode.isDouble()) {
      return JsonNodeType.DOUBLE;
    }
    return JsonNodeType.NULL;
  }

  public String nodeTypeAsString() {
    return nodeType().typeName();
  }

  public boolean isString() {
    return jsonNode.isTextual();
  }

  public boolean isInt() {
    return jsonNode.isInt();
  }

  public boolean isLong() {
    return jsonNode.isLong();
  }

  public boolean isObject() {
    return jsonNode.isObject();
  }

  public boolean isArray() {
    return jsonNode.isArray();
  }

  private Object toPrimitive(JsonNode node) {
    if (node.isObject()) {
      Map<String, Object> map = new HashMap<>();
      node.fieldNames()
          .forEachRemaining(
              fieldName -> {
                JsonNode childNode = node.get(fieldName);
                map.put(fieldName, toPrimitive(childNode));
              });
      return map;
    } else if (node.isArray()) {
      List<Object> list = new ArrayList<>();
      node.elements().forEachRemaining(element -> list.add(toPrimitive(element)));
      return list;
    } else if (node.isTextual()) {
      return node.asText();
    } else if (node.isInt()) {
      return node.asInt();
    } else if (node.isLong()) {
      return node.asLong();
    } else if (node.isDouble()) {
      return node.asDouble();
    } else if (node.isBoolean()) {
      return node.asBoolean();
    } else if (node.isNull()) {
      return null;
    } else {
      return node.toString();
    }
  }

  public String toJson() {
    return jsonNode.toString();
  }
}
