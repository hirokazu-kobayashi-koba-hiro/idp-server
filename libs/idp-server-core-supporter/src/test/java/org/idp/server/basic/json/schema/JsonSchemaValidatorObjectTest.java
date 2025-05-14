package org.idp.server.basic.json.schema;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.junit.jupiter.api.Test;

public class JsonSchemaValidatorObjectTest {

  @Test
  void test_nested_object_missing_required() {
    String schema =
        """
      {
                     "type": "object",
                     "required": ["name", "url"],
                     "properties": {
                       "name": {
                         "type": "string",
                         "minLength": 2
                       },
                       "url": {
                         "type": "string",
                         "format": "uri"
                       },
                       "address": {
                         "type": "object",
                         "required": ["name"],
                         "properties": {
                           "name": {
                             "type": "string",
                             "minLength": 2
                           }
                         }
                       }
                     }
                   }
    """;
    JsonSchemaDefinition jsonSchemaDefinition =
        new JsonSchemaDefinition(JsonNodeWrapper.fromString(schema));

    JsonNodeWrapper value =
        JsonNodeWrapper.fromObject(
            Map.of("name", "Koba", "url", "https://example.com", "address", Map.of()));

    JsonSchemaValidator jsonSchemaValidator = new JsonSchemaValidator(jsonSchemaDefinition);
    JsonSchemaValidationResult validate = jsonSchemaValidator.validate(value);
    for (String error : validate.errors()) {
      assertEquals("address.name is missing", error);
    }
  }
}
