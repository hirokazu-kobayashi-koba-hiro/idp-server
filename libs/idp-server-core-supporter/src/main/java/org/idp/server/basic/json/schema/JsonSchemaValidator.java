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

import java.util.ArrayList;
import java.util.List;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.basic.json.schema.format.JsonPropertyFormat;

public class JsonSchemaValidator {

  JsonSchemaDefinition schemaDefinition;

  public JsonSchemaValidator(JsonSchemaDefinition schemaDefinition) {
    this.schemaDefinition = schemaDefinition;
  }

  public JsonSchemaValidationResult validate(JsonNodeWrapper target) {
    List<String> errors = new ArrayList<>();
    if (!target.exists()) {
      errors.add("Schema does not exist");
      return JsonSchemaValidationResult.failure(errors);
    }

    validateObjectConstraints("", target, schemaDefinition, errors);

    if (!errors.isEmpty()) {
      return JsonSchemaValidationResult.failure(errors);
    }

    return JsonSchemaValidationResult.success();
  }

  void validateObjectConstraints(
      String prefix,
      JsonNodeWrapper target,
      JsonSchemaDefinition jsonSchemaDefinition,
      List<String> errors) {

    if (!target.exists()) {
      // validateRequiredField is covered
      return;
    }

    validateRequiredField(prefix, target, jsonSchemaDefinition, errors);

    for (String field : jsonSchemaDefinition.propertiesFieldAsList()) {
      JsonNodeWrapper valueObject = target.getValueAsJsonNode(field);
      JsonSchemaProperty childSchema = jsonSchemaDefinition.propertySchema(field);
      if (childSchema == null || !childSchema.exists()) {
        continue;
      }

      if (childSchema.isStringType()) {
        validateStringConstraints(prefix, field, valueObject, childSchema, errors);
      } else if (childSchema.isArrayType()) {
        validateArrayConstraints(prefix, field, valueObject, childSchema, errors);
      } else if (childSchema.isObjectType()) {
        JsonNodeWrapper childObject = target.getValueAsJsonNode(field);
        JsonSchemaDefinition childSchemaDefinition = jsonSchemaDefinition.childJsonSchema(field);
        validateObjectConstraints(field, childObject, childSchemaDefinition, errors);
      }
    }
  }

  void validateRequiredField(
      String prefix,
      JsonNodeWrapper target,
      JsonSchemaDefinition jsonSchemaDefinition,
      List<String> errors) {
    List<String> requiredFields = jsonSchemaDefinition.requiredFields();
    if (!requiredFields.isEmpty()) {
      for (String requiredField : requiredFields) {
        if (!target.contains(requiredField)) {
          String composedFiledName = composeFiledName(prefix, requiredField);
          errors.add(composedFiledName + " is missing");
        }
      }
    }
  }

  void validateStringConstraints(
      String prefix,
      String field,
      JsonNodeWrapper valueObject,
      JsonSchemaProperty schemaProperty,
      List<String> errors) {

    if (!valueObject.exists()) {
      // validateRequiredField is covered
      return;
    }

    String value = valueObject.asText();
    String composedFiledName = composeFiledName(prefix, field);

    if (schemaProperty.hasMinLength() && value.length() < schemaProperty.minLength()) {
      errors.add(composedFiledName + " minLength is " + schemaProperty.minLength());
    }

    if (schemaProperty.hasMaxLength() && value.length() > schemaProperty.maxLength()) {
      errors.add(composedFiledName + " maxLength is " + schemaProperty.maxLength());
    }

    if (schemaProperty.hasPattern() && !value.matches(schemaProperty.pattern())) {
      errors.add(composedFiledName + " pattern is " + schemaProperty.pattern());
    }

    if (schemaProperty.hasFormat()) {
      JsonPropertyFormat format = schemaProperty.format();
      if (!format.match(value)) {
        errors.add(
            String.format(
                "%s format is %s, but %s.", field, schemaProperty.formatAsString(), value));
      }
    }

    if (schemaProperty.hasEnum() && !schemaProperty.enumValues().contains(value)) {
      errors.add(
          String.format(
              "%s is not allowed enum value, input: %s, definition: %s",
              field, value, schemaProperty.enumValuesAsString()));
    }
  }

  void validateArrayConstraints(
      String prefix,
      String field,
      JsonNodeWrapper valueObject,
      JsonSchemaProperty schemaProperty,
      List<String> errors) {

    if (!valueObject.exists()) {
      // validateRequiredField is covered
      return;
    }

    List<JsonNodeWrapper> elements = valueObject.elements();
    String composedFiledName = composeFiledName(prefix, field);

    if (schemaProperty.hasMinItems() && elements.size() < schemaProperty.minItems()) {
      errors.add(
          composedFiledName + " must have at least " + schemaProperty.minItems() + " items.");
    }

    if (schemaProperty.hasMaxItems() && elements.size() > schemaProperty.maxItems()) {
      errors.add(composedFiledName + " must have at most " + schemaProperty.maxItems() + " items.");
    }

    if (schemaProperty.uniqueItems()) {
      List<String> seen = new ArrayList<>();
      for (JsonNodeWrapper element : elements) {
        String asString = element.asText();
        if (seen.contains(asString)) {
          errors.add(composedFiledName + " must not contain duplicate items.");
          break;
        }
        seen.add(asString);
      }
    }

    JsonSchemaProperty itemsSchema = schemaProperty.itemsSchema();
    for (JsonNodeWrapper element : elements) {
      if (itemsSchema.isStringType()) {
        validateStringConstraints(prefix, field, element, itemsSchema, errors);
      }
    }
  }

  private String composeFiledName(String prefix, String field) {
    if (prefix.isEmpty()) {
      return field;
    }
    return prefix + "." + field;
  }
}
