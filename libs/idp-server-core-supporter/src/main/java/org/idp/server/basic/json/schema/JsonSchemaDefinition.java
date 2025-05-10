package org.idp.server.basic.json.schema;

import java.util.*;
import org.idp.server.basic.json.JsonNodeWrapper;

public class JsonSchemaDefinition {

  JsonNodeWrapper definition;

  public static JsonSchemaDefinition fromJson(String json) {
    return new JsonSchemaDefinition(JsonNodeWrapper.fromString(json));
  }

  public JsonSchemaDefinition(JsonNodeWrapper definition) {
    this.definition = definition;
  }

  public List<String> requiredFields() {
    if (!definition.contains("required")) {
      return List.of();
    }
    return definition.getValueAsJsonNodeList("required").stream()
        .map(JsonNodeWrapper::asText)
        .toList();
  }

  public JsonNodeWrapper getField(String fieldName) {
    return definition.getValueAsJsonNode(fieldName);
  }

  public Map<String, JsonSchemaProperty> getProperties() {
    JsonNodeWrapper jsonNodeWrapper = definition.getValueAsJsonNode("properties");
    Map<String, JsonSchemaProperty> properties = new HashMap<>();

    jsonNodeWrapper
        .fieldNames()
        .forEachRemaining(
            fieldName -> {
              JsonSchemaProperty jsonSchemaProperty =
                  new JsonSchemaProperty(jsonNodeWrapper.getValueAsJsonNode(fieldName));
              properties.put(fieldName, jsonSchemaProperty);
            });

    return properties;
  }

  public boolean hasProperty(String propertyName) {
    return definition.getValueAsJsonNode("properties").contains(propertyName);
  }

  public JsonSchemaProperty propertySchema(String fieldName) {
    return new JsonSchemaProperty(
        definition.getValueAsJsonNode("properties").getValueAsJsonNode(fieldName));
  }

  public Iterator<String> propertyNames() {
    return definition.fieldNames();
  }
}
