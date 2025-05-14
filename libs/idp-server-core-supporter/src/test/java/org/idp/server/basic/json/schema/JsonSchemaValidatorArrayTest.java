package org.idp.server.basic.json.schema;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.junit.jupiter.api.Test;

public class JsonSchemaValidatorArrayTest {

  JsonSchemaProperty fromJson(String json) {
    JsonNodeWrapper wrapper = JsonNodeWrapper.fromString(json);
    return new JsonSchemaProperty(wrapper);
  }

  JsonSchemaValidator jsonSchemaValidator() {
    String json =
        """
                {
                "type": "object",
                 "properties": {}
                }
                """;
    JsonNodeWrapper wrapper = JsonNodeWrapper.fromString(json);
    JsonSchemaDefinition jsonSchemaDefinition = new JsonSchemaDefinition(wrapper);
    return new JsonSchemaValidator(jsonSchemaDefinition);
  }

  @Test
  void test_valid_array() {
    JsonSchemaValidator jsonSchemaValidator = jsonSchemaValidator();
    String json =
        """
        {
          "type": "array",
          "min_items": 2,
          "max_items": 5,
          "unique_items": true
        }
        """;
    JsonSchemaProperty schemaProperty = fromJson(json);
    JsonNodeWrapper value = JsonNodeWrapper.fromObject(Map.of("tags", List.of("a", "b", "c")));

    List<String> errors = new ArrayList<>();
    jsonSchemaValidator.validateArrayConstraints(
        "tags", value.getValueAsJsonNode("tags"), schemaProperty, errors);

    assertTrue(errors.isEmpty());
  }

  @Test
  void test_min_items_violation() {
    JsonSchemaValidator jsonSchemaValidator = jsonSchemaValidator();
    String json =
        """
        {
          "type": "array",
          "min_items": 2
        }
        """;
    JsonSchemaProperty schemaProperty = fromJson(json);
    JsonNodeWrapper value = JsonNodeWrapper.fromObject(Map.of("tags", List.of("x")));

    List<String> errors = new ArrayList<>();
    jsonSchemaValidator.validateArrayConstraints(
        "tags", value.getValueAsJsonNode("tags"), schemaProperty, errors);

    assertEquals(List.of("tags must have at least 2 items."), errors);
  }

  @Test
  void test_max_items_violation() {
    JsonSchemaValidator jsonSchemaValidator = jsonSchemaValidator();
    String json =
        """
        {
          "type": "array",
          "max_items": 5
        }
        """;
    JsonSchemaProperty schemaProperty = fromJson(json);
    JsonNodeWrapper value =
        JsonNodeWrapper.fromObject(Map.of("tags", List.of("x", "y", "z", "a", "b", "c")));

    List<String> errors = new ArrayList<>();
    jsonSchemaValidator.validateArrayConstraints(
        "tags", value.getValueAsJsonNode("tags"), schemaProperty, errors);

    assertEquals(List.of("tags must have at most 5 items."), errors);
  }

  @Test
  void test_unique_items_violation() {
    JsonSchemaValidator jsonSchemaValidator = jsonSchemaValidator();
    String json =
        """
        {
          "type": "array",
          "unique_items": true
        }
        """;
    JsonSchemaProperty schemaProperty = fromJson(json);
    JsonNodeWrapper value = JsonNodeWrapper.fromObject(Map.of("tags", List.of("a", "b", "a")));

    List<String> errors = new ArrayList<>();
    jsonSchemaValidator.validateArrayConstraints(
        "tags", value.getValueAsJsonNode("tags"), schemaProperty, errors);

    assertEquals(List.of("tags must not contain duplicate items."), errors);
  }
}
