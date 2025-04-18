package org.idp.server.core.basic.json;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

public class JsonNodeWrapper {
  JsonNode jsonNode;

  public JsonNodeWrapper(JsonNode jsonNode) {
    this.jsonNode = jsonNode;
  }

  public Iterator<String> fieldNames() {
    return jsonNode.fieldNames();
  }

  public JsonNodeWrapper getValueAsJsonNode(String fieldName) {
    return new JsonNodeWrapper(jsonNode.get(fieldName));
  }

  public List<JsonNodeWrapper> getValueAsJsonNodeList(String fieldName) {
    List<JsonNodeWrapper> values = new ArrayList<>();
    Iterator<JsonNode> iterator = jsonNode.get(fieldName).elements();
    while (iterator.hasNext()) {
      values.add(new JsonNodeWrapper(iterator.next()));
    }
    return values;
  }

  public boolean contains(String fieldName) {
    return jsonNode.has(fieldName);
  }

  public Object node() {
    return jsonNode;
  }

  public String getValueOrEmptyAsString(String fieldName) {
    return jsonNode.get(fieldName).asText("");
  }

  public int getValueAsInt(String fieldName) {
    return jsonNode.get(fieldName).asInt();
  }

  public String asText() {
    return jsonNode.asText();
  }

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
}
