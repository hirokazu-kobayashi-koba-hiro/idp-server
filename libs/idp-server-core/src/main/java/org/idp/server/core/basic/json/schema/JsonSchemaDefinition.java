package org.idp.server.core.basic.json.schema;

import java.util.List;
import org.idp.server.core.basic.json.JsonNodeWrapper;

public class JsonSchemaDefinition {

  JsonNodeWrapper definition;

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

  public boolean hasProperty(String propertyName) {
    return definition.getValueAsJsonNode("properties").contains(propertyName);
  }

  public JsonSchemaProperty propertySchema(String fieldName) {
    return new JsonSchemaProperty(
        definition.getValueAsJsonNode("properties").getValueAsJsonNode(fieldName));
  }
}
