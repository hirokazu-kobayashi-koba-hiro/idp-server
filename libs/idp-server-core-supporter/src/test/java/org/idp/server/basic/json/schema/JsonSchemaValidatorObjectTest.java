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
